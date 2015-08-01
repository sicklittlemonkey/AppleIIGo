
/**
 * AppleIIGo
 * Display processing
 * (C) 2006 by Marc S. Ressl (ressl@lonetree.com)
 * Released under the GPL
 */

import java.io.*;
import java.applet.*;
import java.awt.*;
import java.awt.image.*;

import javax.imageio.ImageIO;

/**
 * AppleDisplay class<p>
 * Refreshes the display
 */
public class AppleDisplay implements Runnable {
	// Instances of other classes
    private Applet applet;
    private EmAppleII apple;

	// Configuration variables
	public static final int COLORMODE_GREEN = 0;
	public static final int COLORMODE_COLOR = 1;
	
	private int colorMode;
	private boolean isGlare;
	private boolean isStatMode;

	// Refresh
	private int refreshRate;
	private long refreshInterval;
	private long refreshDelayCumulative;
	private long refreshDelayPerSecond;
	private long refreshCycle;

	private boolean isPrecalcRequested = true;
	private boolean isRefreshRequested = true;
	
	// Graphics interface variables
    private boolean[] graphicsDirty = new boolean[0x6000 >> 7];
	private int graphicsMode;

	// Display
	private static final int DISPLAY_CHAR_SIZE_X = 7;
	private static final int DISPLAY_CHAR_SIZE_Y = 8;
	private static final int DISPLAY_CHAR_COUNT_X = 80;
	private static final int DISPLAY_CHAR_COUNT_Y = 24;
	private static final int DISPLAY_SIZE_X = DISPLAY_CHAR_COUNT_X * DISPLAY_CHAR_SIZE_X;
	private static final int DISPLAY_SIZE_Y = DISPLAY_CHAR_COUNT_Y * DISPLAY_CHAR_SIZE_Y;

	// Display composition
	private BufferedImage displayImage;
	private int[] displayImageBuffer;
	private BufferedImage displayImagePaused;
	private BufferedImage displayImageGlare;
	
	// Display scale
	private float displayScale;
	private int displayScaledSizeX;
	private int displayScaledSizeY;	

	// Display palette
	private int[] displayPalette;
	private static final int[] displayPaletteGreen = {
		0x000000, 0x0e470e, 0x041204, 0x166e16,
		0x0f4a0f, 0x115411, 0x0c3b0c, 0x1f9e1f,
		0x125c12, 0x1b8a1b, 0x22ab22, 0x24b524,
		0x1A871a, 0x2de32d, 0x25bd25, 0x32ff32
		};
	private static final int[] displayPaletteColor = {
		0x000000, 0xdd0033, 0x000099, 0xdd22dd,
		0x007722, 0x555555, 0x2222ff, 0x66aaff,
		0x885500, 0xff6600, 0xaaaaaa, 0xff9988,
		0x11dd00, 0xffff00, 0x44ff99, 0xffffff
		};

	// Character stuff
	private final int CHARSET_CHAR_SIZE_X = 8;
	private final int CHARSET_CHAR_SIZE_Y = 8;
	private final int CHARSET_SIZE_X = 0x100 * CHARSET_CHAR_SIZE_X;
	private final int CHARSET_SIZE_Y = CHARSET_CHAR_SIZE_Y;

	private static final int CHARMAP_NORMAL = 0;
	private static final int CHARMAP_FLASH = 1;
	private static final int CHARMAP_ALT = 2;

	private int[] charSet = new int[CHARSET_SIZE_X * CHARSET_CHAR_SIZE_Y];
	private int[] charMap;
	private int[][] charMaps = new int[3][0x100];
	private long charMapFlashCycle = 0;
	private boolean isCharMapFlash = false;

	private int[][] charMapLookup = {
		{0xc0,0xa0,0x40,0x20,0x40,0x20,0x40,0x60},
		{0xc0,0xa0,0xc0,0xa0,0x40,0x20,0x40,0x60},
		{0xc0,0xa0,0x00,0xe0,0x40,0x20,0x40,0x60},
	};

	private static final int[] textLineAddress = {
		0x0000,0x0080,0x0100,0x0180,0x0200,0x0280,0x0300,0x0380,
		0x0028,0x00a8,0x0128,0x01a8,0x0228,0x02a8,0x0328,0x03a8,
		0x0050,0x00d0,0x0150,0x01d0,0x0250,0x02d0,0x0350,0x03d0,
	};

	// Hires stuff
	private int hiresEvenOddToWord[] = new int[0x200];
	private int hiresWord[] = new int[8];
	private int hiresWordNext[] = new int[8];
	private int hiresLookup[] = new int[0x100];	// Bits: [NNccccPP] - Next, current, Previous bits
	private static final int hiresLookupColor[] = {
		// Bits: [PPNNcccc] - Previous, Next, current bits => 4 pixel @ 4 bit color output
// Color-bleeding algorithm
		0x0000,0x0111,0x2222,0x2333,0x4440,0x4551,0x6662,0x6773,0x8800,0x8911,0xaa22,0xab33,0xcc40,0xcd51,0xee62,0xef73,	// 00cccc00
		0x1000,0x1111,0x3222,0x3333,0x5440,0x5551,0x7662,0x7773,0x9800,0x9911,0xba22,0xbb33,0xdc40,0xdd51,0xfe62,0xff73,	// 01cccc00
		0x0000,0x0111,0x2222,0x2333,0x4440,0x4551,0x6662,0x6773,0x8800,0x8911,0xaa22,0xab33,0xcc40,0xcd51,0xee62,0xef73,	// 10cccc00
		0x1000,0x1111,0x3222,0x3333,0x5440,0x5551,0x7662,0x7773,0x9800,0x9911,0xba22,0xbb33,0xdc40,0xdd51,0xfe62,0xff73,	// 11cccc00
		0x0004,0x0115,0x2226,0x2337,0x4444,0x4555,0x6666,0x6777,0x8804,0x8915,0xaa26,0xab37,0xcc44,0xcd55,0xee66,0xef77,	// 00cccc01
		0x1004,0x1115,0x3226,0x3337,0x5444,0x5555,0x7666,0x7777,0x9804,0x9915,0xba26,0xbb37,0xdc44,0xdd55,0xfe66,0xff77,	// 01cccc01
		0x0004,0x0115,0x2226,0x2337,0x4444,0x4555,0x6666,0x6777,0x8804,0x8915,0xaa26,0xab37,0xcc44,0xcd55,0xee66,0xef77,	// 10cccc01
		0x1004,0x1115,0x3226,0x3337,0x5444,0x5555,0x7666,0x7777,0x9804,0x9915,0xba26,0xbb37,0xdc44,0xdd55,0xfe66,0xff77,	// 11cccc01
		0x0088,0x0199,0x22aa,0x23bb,0x44c8,0x45d9,0x66ea,0x67fb,0x8888,0x8999,0xaaaa,0xabbb,0xccc8,0xcdd9,0xeeea,0xeffb,	// 00cccc10
		0x1088,0x1199,0x32aa,0x33bb,0x54c8,0x55d9,0x76ea,0x77fb,0x9888,0x9999,0xbaaa,0xbbbb,0xdcc8,0xddd9,0xfeea,0xfffb,	// 01cccc10
		0x0088,0x0199,0x22aa,0x23bb,0x44c8,0x45d9,0x66ea,0x67fb,0x8888,0x8999,0xaaaa,0xabbb,0xccc8,0xcdd9,0xeeea,0xeffb,	// 10cccc10
		0x1088,0x1199,0x32aa,0x33bb,0x54c8,0x55d9,0x76ea,0x77fb,0x9888,0x9999,0xbaaa,0xbbbb,0xdcc8,0xddd9,0xfeea,0xfffb,	// 11cccc10
		0x008c,0x019d,0x22ae,0x23bf,0x44cc,0x45dd,0x66ee,0x67ff,0x888c,0x899d,0xaaae,0xabbf,0xcccc,0xcddd,0xeeee,0xefff,	// 00cccc11
		0x108c,0x119d,0x32ae,0x33bf,0x54cc,0x55dd,0x76ee,0x77ff,0x988c,0x999d,0xbaae,0xbbbf,0xdccc,0xdddd,0xfeee,0xffff,	// 01cccc11
		0x008c,0x019d,0x22ae,0x23bf,0x44cc,0x45dd,0x66ee,0x67ff,0x888c,0x899d,0xaaae,0xabbf,0xcccc,0xcddd,0xeeee,0xefff,	// 10cccc11
		0x108c,0x119d,0x32ae,0x33bf,0x54cc,0x55dd,0x76ee,0x77ff,0x988c,0x999d,0xbaae,0xbbbf,0xdccc,0xdddd,0xfeee,0xffff,	// 11cccc11

// First table
		0x0000,0x0001,0x0020,0x0033,0x0400,0x0505,0x0660,0x0777,0x8000,0x9009,0xa0a0,0xb0bb,0xcc00,0xdd0d,0xeee0,0xffff,	// 00cccc00
		0x0000,0x1111,0x0020,0x0033,0x0400,0x0505,0x0660,0x0777,0x8000,0x9009,0xa0a0,0xb0bb,0xcc00,0xdd0d,0xeee0,0xffff,	// 01cccc00
		0x0000,0x0001,0x2222,0x0033,0x0400,0x0505,0x0660,0x0777,0x8000,0x9009,0xa0a0,0xb0bb,0xcc00,0xdd0d,0xeee0,0xffff,	// 10cccc00
		0x0000,0x0001,0x0020,0x3333,0x0400,0x0505,0x0660,0x0777,0xf000,0x9009,0xa0a0,0xb0bb,0xff00,0xdd0d,0xfff0,0xffff,	// 11cccc00
		0x0000,0x0001,0x0020,0x0033,0x4444,0x0505,0x0660,0x0777,0x8000,0x9009,0xa0a0,0xb0bb,0xcc00,0xdd0d,0xeee0,0xffff,	// 00cccc01
		0x0000,0x0001,0x0020,0x0033,0x0400,0x5555,0x0660,0x0777,0x8000,0x9009,0xa0a0,0xb0bb,0xcc00,0xdd0d,0xeee0,0xffff,	// 01cccc01
		0x0000,0x0001,0x0020,0x0033,0x0400,0x0505,0x6666,0x0777,0x8000,0x9009,0xa0a0,0xb0bb,0xcc00,0xdd0d,0xeee0,0xffff,	// 10cccc01
		0x0000,0x0001,0x0020,0x0033,0x0400,0x0505,0x0660,0x7777,0x8000,0x9009,0xa0a0,0xb0bb,0xcc00,0xdd0d,0xeee0,0xffff,	// 11cccc01
		0x0000,0x0001,0x0020,0x0033,0x0400,0x0505,0x0660,0x0fff,0x8888,0x9009,0xa0a0,0xb0bb,0xcc00,0xdd0d,0xeee0,0xffff,	// 00cccc10
		0x0000,0x0001,0x0020,0x0033,0x0400,0x0505,0x0660,0x0777,0x8000,0x9999,0xa0a0,0xb0bb,0xcc00,0xdd0d,0xeee0,0xffff,	// 01cccc10
		0x0000,0x0001,0x0020,0x0033,0x0400,0x0505,0x0660,0x0777,0x8000,0x9009,0xaaaa,0xb0bb,0xcc00,0xdd0d,0xeee0,0xffff,	// 10cccc10
		0x0000,0x0001,0x0020,0x0033,0x0400,0x0505,0x0660,0x0777,0x8000,0x9009,0xa0a0,0xbbbb,0xcc00,0xdd0d,0xeee0,0xffff,	// 11cccc10
		0x0000,0x000f,0x0020,0x00ff,0x0400,0x0505,0x0660,0x0fff,0x8000,0x9009,0xa0a0,0xb0bb,0xcccc,0xdd0d,0xeee0,0xffff,	// 00cccc11
		0x0000,0x0001,0x0020,0x0033,0x0400,0x0505,0x0660,0x0777,0x8000,0x9009,0xa0a0,0xb0bb,0xcc00,0xdddd,0xeee0,0xffff,	// 01cccc11
		0x0000,0x0001,0x0020,0x0033,0x0400,0x0505,0x0660,0x0777,0x8000,0x9009,0xa0a0,0xb0bb,0xcc00,0xdd0d,0xeeee,0xffff,	// 10cccc11
		0x0000,0x000f,0x00f0,0x00ff,0x0f00,0x0f0f,0x0ff0,0x0fff,0xf000,0xf00f,0xf0f0,0xf0ff,0xff00,0xff0f,0xfff0,0xffff,	// 11cccc11

		};
	private int[] doubleHiresPalette;
	private static final int[] doubleHiresPaletteColor = {
		0x000000, 0x000099, 0x007722, 0x2222ff, 
		0x885500, 0xaaaaaa, 0x11dd00, 0x44ff99, 
		0xdd0033, 0xdd22dd, 0x555555, 0x66aaff,
		0xff6600, 0xff9988, 0xffff00, 0xffffff,
		};

	
	// Thread stuff
	private boolean isPaused = true;
	private Thread thread;
	private String threadError = null;
	


	/**
	 * AppleDisplay class constructor
	 *
	 * @param	apple	The EmAppleII instance
	 */
    public AppleDisplay(Applet applet, EmAppleII apple) {
		this.applet = applet;
		this.apple = apple;
		
		// Create display image
		displayImage = new BufferedImage(
			DISPLAY_SIZE_X,
			DISPLAY_SIZE_Y,
			BufferedImage.TYPE_INT_RGB);
		displayImageBuffer = ((DataBufferInt) displayImage.getRaster().getDataBuffer()).getData();

		// Load glare and pause images
		try {
			displayImageGlare = ImageIO.read(this.getClass().getResourceAsStream(
				"/Resources/Glare.png"));
			displayImagePaused = ImageIO.read(this.getClass().getResourceAsStream(
				"/Resources/Paused.png"));
		} catch (IOException e) {
			threadError = e.toString();
		}

		// Character maps
		precalcCharMaps();

		// Hires
		precalcHiresEvenEddToWord();

		// Set parameters
		setScale(1.0f);
		setRefreshRate(10);
		setColorMode(COLORMODE_GREEN);
		setGlare(false);
		setStatMode(false);
	}

	/**
	 * Set scale
	 */
	public void setScale(float value) {
		if (value <= 0.0f)
			return;
			
		displayScale = value;
		isPrecalcRequested = true;
    }
	
	/**
	 * Get scale
	 */
	public float getScale() {
		return displayScale;
    }
	
	/**
	 * Set refresh rate
	 *
	 * @param	value	Display refresh rate in mHz
	 */
	public void setRefreshRate(int value) {
		if (value <= 0.0f)
			return;
			
		this.refreshRate = value;
		refreshInterval = (int) (1000.0 / value);
    }
	
	/**
	 * Get refresh rate
	 */
	public int getRefreshRate() {
		return refreshRate;
    }
	
	/**
	 * Set color mode
	 */
	public void setColorMode(int value) {
		colorMode = value;
		isPrecalcRequested = true;
    }
	
	/**
	 * Get color mode
	 */
	public int getColorMode() {
		return colorMode;
    }
	
	/**
	 * Set paused
	 */
	public void setPaused(boolean value) {
		if (isPaused == value)
			return;

		isPaused = value;
		if (isPaused) {
			try {
				thread.join(1000);
			} catch (InterruptedException e) {
			}
			applet.repaint();
		} else {
			isRefreshRequested = true;
			thread = new Thread(this);
			thread.start();
		}
    }
	
	/**
	 * Get glare
	 */
	public boolean getPaused() {
		return isPaused;
    }

	/**
	 * Set glare
	 */
	public void setGlare(boolean value) {
		isGlare = value;
		isRefreshRequested = true;
    }
	
	/**
	 * Get glare
	 */
	public boolean getGlare() {
		return isGlare;
    }

	/**
	 * Set stat mode
	 */
	public void setStatMode(boolean value) {
		isStatMode = value;
		isRefreshRequested = true;
    }

	/**
	 * Get stat mode
	 */
	public boolean getStatMode() {
		return isStatMode;
    }

	/**
 	 * Step instruction in debug mode
	 */
	public String getStatInfo() {
		String statInfo = "";
		long refreshRateCurrent;

		// Calculate effective CPU speed
		if (refreshDelayPerSecond > 1000)
			refreshRateCurrent = refreshRate * 1000 / refreshDelayPerSecond;
		else
			refreshRateCurrent = refreshRate;

		// Return FPS
		statInfo += " FPS=" + refreshRateCurrent + " [" + refreshDelayPerSecond + " ms/s]\n";
		statInfo += " GM=" + graphicsMode + "\n";
		if (threadError != null)
			statInfo = statInfo.concat(threadError + "\n");

		return statInfo;
	}

	/**
	 * Paint function
	 *
	 * @param	g		Graphics object
	 */
    public void paint(Graphics g) {
		if (displayImage != null)
			g.drawImage(displayImage,
				0, 0, displayScaledSizeX, displayScaledSizeY,
				0, 0, DISPLAY_SIZE_X, DISPLAY_SIZE_Y,
				applet);

		if (isStatMode) {
			g.setColor(Color.black);
			g.fillRect(0,0,256,128); 
			drawStatInfo(g);
		}
		if ((displayImagePaused != null) && isPaused)
			g.drawImage(displayImagePaused,
				0, 0, displayScaledSizeX, displayScaledSizeY,
				0, 0, DISPLAY_SIZE_X, DISPLAY_SIZE_Y,
				applet);

		if (isGlare && (displayImageGlare != null))
			g.drawImage(displayImageGlare,
				0, 0, displayScaledSizeX, displayScaledSizeY,
				0, 0, DISPLAY_SIZE_X, DISPLAY_SIZE_Y,
				applet);
	}

	/**
	 * Display refresh thread
	 */
    public void run() {
		try {
			while (!isPaused) {
				long refreshStart = System.currentTimeMillis();
				long refreshDelay;

				refreshDisplay();

				refreshDelay = System.currentTimeMillis() - refreshStart;

				refreshDelayCumulative += refreshDelay;
				refreshCycle++;
				if (refreshCycle >= refreshRate) {
					refreshDelayPerSecond = refreshDelayCumulative;
					refreshDelayCumulative = refreshCycle = 0;
				}

				if (refreshDelay < refreshInterval)
					Thread.sleep(refreshInterval - refreshDelay);
			}
		} catch (InterruptedException e) {
		};
	}
	
	
	
	/**
	 * Paint stat info
	 */
	private void drawStatInfo(Graphics g) {
		final int fontSize = 16;

		g.setColor(Color.WHITE);

		String statInfo = apple.getStatInfo() + "\n" + getStatInfo();
		String[] lines = statInfo.split("\n");

		int drawPosY = fontSize;
		for(int lineNo = 0; lineNo < lines.length; lineNo++) {
			String line = lines[lineNo];
			g.drawString(line, 0, drawPosY);
			drawPosY += fontSize;
		}
	}
	
	/**
	 * Display refresh
	 */
	private void refreshDisplay() {
		boolean isCharsetUpdateRequested = false;
		boolean isSetDirtyRequested = false;
		boolean isSetHiresDirtyRequested = false;
		boolean isRenderRequested = false;

		// Precalculation
		if (isPrecalcRequested) {
			isPrecalcRequested = false;
			precalcDisplay();
			graphicsMode = -1;
		}

		// Repaint if graphics mode changes
		if (graphicsMode != apple.graphicsMode) {
			graphicsMode = apple.graphicsMode;
			isCharsetUpdateRequested = true;
			isSetHiresDirtyRequested = true;
		}

		// Periodic refresh
		if (charMapFlashCycle <= 0) {
			charMapFlashCycle = (int) (refreshRate / 4 - 1);
			isCharMapFlash = !isCharMapFlash;
			isCharsetUpdateRequested = true;
		} else
			charMapFlashCycle--;

		// Some internal variables
		boolean isSomeText = ((graphicsMode & (EmAppleII.GR_TEXT | EmAppleII.GR_MIXMODE)) != 0);
		boolean isSomeLores = ((graphicsMode & (EmAppleII.GR_TEXT | EmAppleII.GR_HIRES)) == 0);
		boolean isSomeHires = ((graphicsMode & (EmAppleII.GR_TEXT | EmAppleII.GR_HIRES)) == EmAppleII.GR_HIRES);

		boolean isMixedMode = ((graphicsMode & (EmAppleII.GR_TEXT | EmAppleII.GR_MIXMODE)) == EmAppleII.GR_MIXMODE);
		boolean isPage2 = ((graphicsMode & (EmAppleII.GR_80STORE | EmAppleII.GR_PAGE2)) == EmAppleII.GR_PAGE2);
		boolean isAltChar = ((graphicsMode & EmAppleII.GR_ALTCHAR) != 0);
		boolean isDoubleTextMode = ((graphicsMode & EmAppleII.GR_80CHAR) == EmAppleII.GR_80CHAR);
		boolean isDoubleGraphicsMode = ((graphicsMode & (EmAppleII.GR_80CHAR | EmAppleII.GR_DHIRES)) == (EmAppleII.GR_80CHAR | EmAppleII.GR_DHIRES));

		int baseAddressText = isPage2 ? EmAppleII.MEM_MAIN_RAM2 : EmAppleII.MEM_MAIN_TEXT;
		int baseAddressHires = isPage2 ? EmAppleII.MEM_MAIN_RAM3 : EmAppleII.MEM_MAIN_HIRES;

		// Set char map
		if (isCharsetUpdateRequested) {
			if (isAltChar)
				setCharMap(CHARMAP_ALT);
			else if (isCharMapFlash)
				setCharMap(CHARMAP_FLASH);
			else
				setCharMap(CHARMAP_NORMAL);

			isSetDirtyRequested = true;
			isRefreshRequested = true;
		}
		
		// Refresh dirty buffers?
		if (isSetDirtyRequested) {
			if (isSomeText || isSomeLores)
				setTextBufferDirty(baseAddressText);

			if (isSetHiresDirtyRequested && isSomeHires)
				setHiresBufferDirty(baseAddressHires);

			isRenderRequested = true;
		} else {
			if ((isSomeText || isSomeLores) && isTextBufferDirty(baseAddressText))
				isRenderRequested = true;

			if (isSomeHires && isHiresBufferDirty(baseAddressHires))
				isRenderRequested = true;
		}

		// Draw
		if (isRenderRequested) {
			if (isSomeText) {
				if (isDoubleTextMode)
					renderDoubleText(baseAddressText, isMixedMode);
				else
					renderText(baseAddressText, isMixedMode);
			}
			
			if (isSomeHires) {
				if (isDoubleGraphicsMode)
					renderDoubleHires(baseAddressHires, isMixedMode);
				else
					renderHires(baseAddressHires, isMixedMode);
			} else if (isSomeLores) {
				if (isDoubleGraphicsMode)
					renderDoubleLores(baseAddressText, isMixedMode);
				else
					renderLores(baseAddressText, isMixedMode);
			}

			isRefreshRequested = true;
		}

		if (isRefreshRequested) {
			isRefreshRequested = false;
			applet.repaint();
		}
	}

	/**
	 * Set text buffer dirty
	 */
	private void setTextBufferDirty(int baseAddress) {
		// Update dirty
		int addressStart = baseAddress >> 7;
		int addressEnd = addressStart + 8;
		for (int address = addressStart; address < addressEnd; address++) {
			graphicsDirty[address] = true;
			apple.graphicsDirty[address] = false;
		}
	}

	/**
	 * Set hires buffer dirty
	 */
	private void setHiresBufferDirty(int baseAddress) {
		// Update dirty
		int addressStart = baseAddress >> 7;
		int addressEnd = addressStart + 8;
		for (int address = addressStart; address < addressEnd; address++) {
			graphicsDirty[address] = true;
			apple.graphicsDirty[address + (0x0000 >> 7)] = false;
			apple.graphicsDirty[address + (0x0400 >> 7)] = false;
			apple.graphicsDirty[address + (0x0800 >> 7)] = false;
			apple.graphicsDirty[address + (0x0c00 >> 7)] = false;
			apple.graphicsDirty[address + (0x1000 >> 7)] = false;
			apple.graphicsDirty[address + (0x1400 >> 7)] = false;
			apple.graphicsDirty[address + (0x1800 >> 7)] = false;
			apple.graphicsDirty[address + (0x1c00 >> 7)] = false;
		}
	}

	/**
	 * Is text buffer dirty?
	 */
	private boolean isTextBufferDirty(int baseAddress) {
		boolean isDirty = false;

		// Update dirty
		int addressStart = baseAddress >> 7;
		int addressEnd = addressStart + 8;
		for (int address = addressStart; address < addressEnd; address++) {
			graphicsDirty[address] = apple.graphicsDirty[address];
			apple.graphicsDirty[address] = false;
			if (graphicsDirty[address])
				isDirty = true;
		}
		
		return isDirty;
	}

	/**
	 * Is hires buffer dirty?
	 */
	private boolean isHiresBufferDirty(int baseAddress) {
		boolean isDirty = false;

		// Update dirty
		int addressStart = baseAddress >> 7;
		int addressEnd = addressStart + 8;
		for (int address = addressStart; address < addressEnd; address++) {
			graphicsDirty[address] =
				apple.graphicsDirty[address + (0x0000 >> 7)] || 
				apple.graphicsDirty[address + (0x0400 >> 7)] ||
				apple.graphicsDirty[address + (0x0800 >> 7)] || 
				apple.graphicsDirty[address + (0x0c00 >> 7)] || 
				apple.graphicsDirty[address + (0x1000 >> 7)] || 
				apple.graphicsDirty[address + (0x1400 >> 7)] ||
				apple.graphicsDirty[address + (0x1800 >> 7)] || 
				apple.graphicsDirty[address + (0x1c00 >> 7)];
			apple.graphicsDirty[address + (0x0000 >> 7)] = false;
			apple.graphicsDirty[address + (0x0400 >> 7)] = false;
			apple.graphicsDirty[address + (0x0800 >> 7)] = false;
			apple.graphicsDirty[address + (0x0c00 >> 7)] = false;
			apple.graphicsDirty[address + (0x1000 >> 7)] = false;
			apple.graphicsDirty[address + (0x1400 >> 7)] = false;
			apple.graphicsDirty[address + (0x1800 >> 7)] = false;
			apple.graphicsDirty[address + (0x1c00 >> 7)] = false;
			if (graphicsDirty[address])
				isDirty = true;
		}
		
		return isDirty;
	}
	


	/**
	 * Display precalculation
	 */
	private void precalcDisplay() {
		// Display scaled size
		displayScaledSizeX = (int) (DISPLAY_SIZE_X * displayScale / 2);
		displayScaledSizeY = (int) (DISPLAY_SIZE_Y * displayScale);

		// Prepare display palette
		setDisplayPalette();

		// Prepare character set
		loadCharSet();

		// Prepare hires graphics
		precalcHiresLookup();
	}

	/**
	 * Precalculate charSet
	 */
	private void loadCharSet() {
		final int CHARSET_SOURCE_CHAR_COUNT = 128;
		final int CHARSET_SOURCE_SIZE_X = CHARSET_SOURCE_CHAR_COUNT * CHARSET_CHAR_SIZE_X;
		final int CHARSET_SOURCE_SIZE_Y = CHARSET_CHAR_SIZE_Y;

		int charSetOffset = 0;

		// Get RGB image
		try {
			BufferedImage charSetSource = ImageIO.read(this.getClass().getResourceAsStream(
				"/Resources/Character Set.png")); 

			charSetSource.getRGB(
				0, 0,
				CHARSET_SOURCE_SIZE_X, CHARSET_SOURCE_SIZE_Y,
				charSet,
				0, CHARSET_SIZE_X);
		} catch (IOException e) {
			threadError = e.toString();
		}

		// Duplicate and invert
		for(int charSetPosY = 0; charSetPosY < CHARSET_SIZE_Y; charSetPosY++) {
			for(int charSetPosX = 0; charSetPosX < CHARSET_SOURCE_SIZE_X; charSetPosX++) {
				charSet[charSetOffset + CHARSET_SOURCE_SIZE_X + charSetPosX] = 
					charSet[charSetOffset + charSetPosX] ^ 0xffffff;
			}
			charSetOffset += CHARSET_SIZE_X;
		}
		
		// Colorize
		for(charSetOffset = 0; charSetOffset < (CHARSET_SIZE_X * CHARSET_SIZE_Y); charSetOffset++)
			charSet[charSetOffset] &= (displayPalette[0xf] | 0xff000000);
	}

	/**
	 * Precalculate char map
	 */
	private void precalcCharMaps() {
		for(int index = 0; index < 3; index++) {
			for(int character = 0; character < 0x100; character++)
				charMaps[index][character] = charMapLookup[index][character >> 5] + (character & 0x1f);
		}
	}

	/**
	 * Set char map
	 */
	private void setCharMap(int value) {
		charMap = charMaps[value];
	}

	/**
	 * Set lores palette
	 */
	private void setDisplayPalette() {
		displayPalette = (colorMode == COLORMODE_COLOR) ? displayPaletteColor : displayPaletteGreen;
		doubleHiresPalette = (colorMode == COLORMODE_COLOR) ? doubleHiresPaletteColor : displayPaletteGreen;
	}

	/**
	 * Precalculate hires even odd to word
	 */
	private void precalcHiresEvenEddToWord() {
		for(int value = 0; value < 0x200; value++) {
			hiresEvenOddToWord[value] = 
				((value & 0x01) << 0) |
				((value & 0x01) << 1) | 
				((value & 0x02) << 1) |
				((value & 0x02) << 2) | 
				((value & 0x04) << 2) |
				((value & 0x04) << 3) | 
				((value & 0x08) << 3) |
				((value & 0x08) << 4) | 
				((value & 0x10) << 4) |
				((value & 0x10) << 5) | 
				((value & 0x20) << 5) |
				((value & 0x20) << 6) | 
				((value & 0x40) << 6) |
				((value & 0x40) << 7);

			if ((value & 0x80) != 0) {
				hiresEvenOddToWord[value] <<= 1;
				hiresEvenOddToWord[value] |= ((value & 0x100) >> 8);
			}
		}
	}

	/**
	 * Precalculate hires
	 */
	private void precalcHiresLookup() {
		if (colorMode == COLORMODE_COLOR) {
			for(int value = 0; value < 0x100; value++)
				hiresLookup[value] = hiresLookupColor[((value << 6) & 0xff) | (value >> 2)];
		} else {
			for(int value = 0; value < 0x100; value++)
				hiresLookup[value] = 
					(((value & 0x04) != 0) ? 0x000f : 0) | 
					(((value & 0x08) != 0) ? 0x00f0 : 0) | 
					(((value & 0x10) != 0) ? 0x0f00 : 0) | 
					(((value & 0x20) != 0) ? 0xf000 : 0);
		}
	}
	


	/**
	 * Render text canvas
	 */
	private final void renderTextScanLine(int destOffset, int sourceOffset) {
		displayImageBuffer[destOffset + 0] = displayImageBuffer[destOffset + 1] = charSet[sourceOffset];
		displayImageBuffer[destOffset + 2] = displayImageBuffer[destOffset + 3] = charSet[sourceOffset + 1];
		displayImageBuffer[destOffset + 4] = displayImageBuffer[destOffset + 5] = charSet[sourceOffset + 2];
		displayImageBuffer[destOffset + 6] = displayImageBuffer[destOffset + 7] = charSet[sourceOffset + 3];
		displayImageBuffer[destOffset + 8] = displayImageBuffer[destOffset + 9] = charSet[sourceOffset + 4];
		displayImageBuffer[destOffset + 10] = displayImageBuffer[destOffset + 11] = charSet[sourceOffset + 5];
		displayImageBuffer[destOffset + 12] = displayImageBuffer[destOffset + 13] = charSet[sourceOffset + 6];
	}
	private final void renderTextCharacter(int destOffset, int sourceOffset) {
		renderTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderTextScanLine(destOffset, sourceOffset);
	}
	private void renderText(int baseAddress, boolean isMixedMode) {
		int screenCharY, screenCharYStart = isMixedMode ? 20 : 0;
		int displayOffset;
		int address, addressEnd, addressStart;
		
		displayOffset = screenCharYStart * DISPLAY_CHAR_SIZE_Y * DISPLAY_SIZE_X;
		for (screenCharY = screenCharYStart; screenCharY < 24; screenCharY++) {
			addressStart = baseAddress + textLineAddress[screenCharY];
			
			if (graphicsDirty[addressStart >> 7]) {
				addressEnd = addressStart + 40;
				
				for (address = addressStart; address < addressEnd; address++) {
					renderTextCharacter(displayOffset, charMap[apple.mem[address] & 0xff] << 3);
					displayOffset += DISPLAY_CHAR_SIZE_X * 2;
				}
				displayOffset += (DISPLAY_CHAR_SIZE_Y - 1) * DISPLAY_SIZE_X;
			} else
				displayOffset += DISPLAY_CHAR_SIZE_Y * DISPLAY_SIZE_X;
		}
	}
	
	/**
	 * Render double text canvas
	 */
	private final void renderDoubleTextScanLine(int destOffset, int sourceOffset) {
		displayImageBuffer[destOffset + 0] = charSet[sourceOffset];
		displayImageBuffer[destOffset + 1] = charSet[sourceOffset + 1];
		displayImageBuffer[destOffset + 2] = charSet[sourceOffset + 2];
		displayImageBuffer[destOffset + 3] = charSet[sourceOffset + 3];
		displayImageBuffer[destOffset + 4] = charSet[sourceOffset + 4];
		displayImageBuffer[destOffset + 5] = charSet[sourceOffset + 5];
		displayImageBuffer[destOffset + 6] = charSet[sourceOffset + 6];
	}
	private final void renderDoubleTextCharacter(int destOffset, int sourceOffset) {
		renderDoubleTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderDoubleTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderDoubleTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderDoubleTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderDoubleTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderDoubleTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderDoubleTextScanLine(destOffset, sourceOffset);
		destOffset += DISPLAY_SIZE_X; sourceOffset += CHARSET_SIZE_X;
		renderDoubleTextScanLine(destOffset, sourceOffset);
	}
	private void renderDoubleText(int baseAddress, boolean isMixedMode) {
		int screenCharY, screenCharYStart = isMixedMode ? 20 : 0;
		int displayOffset;
		int address, addressEnd, addressStart;
		
		displayOffset = screenCharYStart * DISPLAY_CHAR_SIZE_Y * DISPLAY_SIZE_X;
		for (screenCharY = screenCharYStart; screenCharY < 24; screenCharY++) {
			addressStart = baseAddress + textLineAddress[screenCharY];
			
			if (graphicsDirty[addressStart >> 7]) {
				addressEnd = addressStart + 40;
				
				for (address = addressStart; address < addressEnd; address++) {
					renderDoubleTextCharacter(displayOffset, charMap[apple.mem[address + 0x10000] & 0xff] << 3);
					displayOffset += DISPLAY_CHAR_SIZE_X;
					renderDoubleTextCharacter(displayOffset, charMap[apple.mem[address + 0x00000] & 0xff] << 3);
					displayOffset += DISPLAY_CHAR_SIZE_X;
				}
				displayOffset += (DISPLAY_CHAR_SIZE_Y - 1) * DISPLAY_SIZE_X;
			} else
				displayOffset += DISPLAY_CHAR_SIZE_Y * DISPLAY_SIZE_X;
		}
	}
		
	/**
	 * Render lores canvas
	 */
	private final void renderLoresScanLine(int destOffset, int color) {
		displayImageBuffer[destOffset + 0] = color;
		displayImageBuffer[destOffset + 1] = color;
		displayImageBuffer[destOffset + 2] = color;
		displayImageBuffer[destOffset + 3] = color;
		displayImageBuffer[destOffset + 4] = color;
		displayImageBuffer[destOffset + 5] = color;
		displayImageBuffer[destOffset + 6] = color;
	}
	private final void renderLoresBlock(int destOffset, int colorTop, int colorBottom) {
		renderLoresScanLine(destOffset, colorTop);
		destOffset += DISPLAY_SIZE_X;
		renderLoresScanLine(destOffset, colorTop);
		destOffset += DISPLAY_SIZE_X;
		renderLoresScanLine(destOffset, colorTop);
		destOffset += DISPLAY_SIZE_X;
		renderLoresScanLine(destOffset, colorTop);
		destOffset += DISPLAY_SIZE_X;
		renderLoresScanLine(destOffset, colorBottom);
		destOffset += DISPLAY_SIZE_X;
		renderLoresScanLine(destOffset, colorBottom);
		destOffset += DISPLAY_SIZE_X;
		renderLoresScanLine(destOffset, colorBottom);
		destOffset += DISPLAY_SIZE_X;
		renderLoresScanLine(destOffset, colorBottom);
	}
	private void renderLores(int baseAddress, boolean isMixedMode) {
		int screenCharY, screenCharYEnd = isMixedMode ? 20 : 24;
		int displayOffset;
		int address, addressEnd, addressStart;
		
		displayOffset = 0;
		for (screenCharY = 0; screenCharY < screenCharYEnd; screenCharY++) {
			addressStart = baseAddress + textLineAddress[screenCharY];
			
			if (graphicsDirty[addressStart >> 7]) {
				addressEnd = addressStart + 40;
				
				for (address = addressStart; address < addressEnd; address++) {
					renderLoresBlock(displayOffset, 
						displayPalette[apple.mem[address] & 0xf], 
						displayPalette[(apple.mem[address] & 0xf0) >> 4]);
					displayOffset += DISPLAY_CHAR_SIZE_X;
					renderLoresBlock(displayOffset, 
						displayPalette[apple.mem[address] & 0xf], 
						displayPalette[(apple.mem[address] & 0xf0) >> 4]);
					displayOffset += DISPLAY_CHAR_SIZE_X;
				}
				displayOffset += (DISPLAY_CHAR_SIZE_Y - 1) * DISPLAY_SIZE_X;
			} else
				displayOffset += DISPLAY_CHAR_SIZE_Y * DISPLAY_SIZE_X;
		}
	}

	/**
	 * Render double lores canvas
	 */
	private void renderDoubleLores(int baseAddress, boolean isMixedMode) {
		int screenCharY, screenCharYEnd = isMixedMode ? 20 : 24;
		int displayOffset;
		int address, addressEnd, addressStart;
		
		displayOffset = 0;
		for (screenCharY = 0; screenCharY < screenCharYEnd; screenCharY++) {
			addressStart = baseAddress + textLineAddress[screenCharY];
			
			if (graphicsDirty[addressStart >> 7]) {
				addressEnd = addressStart + 40;
				
				for (address = addressStart; address < addressEnd; address++) {
					renderLoresBlock(displayOffset, 
						displayPalette[apple.mem[address + 0x10000] & 0xf], 
						displayPalette[(apple.mem[address + 0x10000] & 0xf0) >> 4]);
					displayOffset += DISPLAY_CHAR_SIZE_X;
					renderLoresBlock(displayOffset, 
						displayPalette[apple.mem[address] & 0xf], 
						displayPalette[(apple.mem[address] & 0xf0) >> 4]);
					displayOffset += DISPLAY_CHAR_SIZE_X;
				}
				displayOffset += (DISPLAY_CHAR_SIZE_Y - 1) * DISPLAY_SIZE_X;
			} else
				displayOffset += DISPLAY_CHAR_SIZE_Y * DISPLAY_SIZE_X;
		}
	}

	/**
	 * Render hires canvas
	 */
	private final void renderHiresWord(int destOffset, int hiresNibble) {
		displayImageBuffer[destOffset + 0] = displayPalette[(hiresNibble >> 0) & 0xf];
		displayImageBuffer[destOffset + 1] = displayPalette[(hiresNibble >> 4) & 0xf];
		displayImageBuffer[destOffset + 2] = displayPalette[(hiresNibble >> 8) & 0xf];
		displayImageBuffer[destOffset + 3] = displayPalette[(hiresNibble >> 12) & 0xf];
	}
	private final void renderHiresScanLine(int destOffset, int hiresWord) {
		renderHiresWord(destOffset + 0, hiresLookup[(hiresWord >> 0) & 0xff]);
		renderHiresWord(destOffset + 4, hiresLookup[(hiresWord >> 4) & 0xff]);
		renderHiresWord(destOffset + 8, hiresLookup[(hiresWord >> 8) & 0xff]);
		renderHiresWord(destOffset + 12, hiresLookup[(hiresWord >> 12) & 0xff]);
		renderHiresWord(destOffset + 16, hiresLookup[(hiresWord >> 16) & 0xff]);
		renderHiresWord(destOffset + 20, hiresLookup[(hiresWord >> 20) & 0xff]);
		renderHiresWord(destOffset + 24, hiresLookup[(hiresWord >> 24) & 0xff]);
	}
	private final void renderHiresBlock(int destOffset) {
		renderHiresScanLine(destOffset, hiresWord[0]); destOffset += DISPLAY_SIZE_X;
		renderHiresScanLine(destOffset, hiresWord[1]); destOffset += DISPLAY_SIZE_X;
		renderHiresScanLine(destOffset, hiresWord[2]); destOffset += DISPLAY_SIZE_X;
		renderHiresScanLine(destOffset, hiresWord[3]); destOffset += DISPLAY_SIZE_X;
		renderHiresScanLine(destOffset, hiresWord[4]); destOffset += DISPLAY_SIZE_X;
		renderHiresScanLine(destOffset, hiresWord[5]); destOffset += DISPLAY_SIZE_X;
		renderHiresScanLine(destOffset, hiresWord[6]); destOffset += DISPLAY_SIZE_X;
		renderHiresScanLine(destOffset, hiresWord[7]);
	}
	private final void resetHiresWords() {
		hiresWord[0] = 0;
		hiresWord[1] = 0;
		hiresWord[2] = 0;
		hiresWord[3] = 0;
		hiresWord[4] = 0;
		hiresWord[5] = 0;
		hiresWord[6] = 0;
		hiresWord[7] = 0;
	}
	private final void bufferHiresWords() {
		hiresWord[0] = hiresWordNext[0];
		hiresWord[1] = hiresWordNext[1];
		hiresWord[2] = hiresWordNext[2];
		hiresWord[3] = hiresWordNext[3];
		hiresWord[4] = hiresWordNext[4];
		hiresWord[5] = hiresWordNext[5];
		hiresWord[6] = hiresWordNext[6];
		hiresWord[7] = hiresWordNext[7];
	}
	private final void calcNextHiresWord(int hiresWordIndex, int byteEven, int byteOdd) {
		hiresWordNext[hiresWordIndex] = hiresWord[hiresWordIndex] >> 28;
		hiresWordNext[hiresWordIndex] |= 
			hiresEvenOddToWord[(byteEven & 0xff) | ((hiresWordNext[hiresWordIndex] & 0x2) << 7)] << 2;
		hiresWordNext[hiresWordIndex] |= 
			hiresEvenOddToWord[(byteOdd & 0xff) | ((hiresWordNext[hiresWordIndex] & 0x8000) >> 7)] << 16;
		hiresWord[hiresWordIndex] |= (hiresWordNext[hiresWordIndex] << 28);
	}
	private final void calcNextHiresWords(int address) {
		calcNextHiresWord(0, apple.mem[address + 0x00000], apple.mem[address + 0x00001]);
		calcNextHiresWord(1, apple.mem[address + 0x00400], apple.mem[address + 0x00401]);
		calcNextHiresWord(2, apple.mem[address + 0x00800], apple.mem[address + 0x00801]);
		calcNextHiresWord(3, apple.mem[address + 0x00c00], apple.mem[address + 0x00c01]);
		calcNextHiresWord(4, apple.mem[address + 0x01000], apple.mem[address + 0x01001]);
		calcNextHiresWord(5, apple.mem[address + 0x01400], apple.mem[address + 0x01401]);
		calcNextHiresWord(6, apple.mem[address + 0x01800], apple.mem[address + 0x01801]);
		calcNextHiresWord(7, apple.mem[address + 0x01c00], apple.mem[address + 0x01c01]);
	}
	private void renderHires(int baseAddress, boolean isMixedMode) {
		int screenCharY, screenCharYEnd = isMixedMode ? 20 : 24;
		int displayOffset;
		int address, addressEnd, addressStart;
		
		displayOffset = 0;
		for (screenCharY = 0; screenCharY < screenCharYEnd; screenCharY++) {
			addressStart = baseAddress + textLineAddress[screenCharY];
			
			if (graphicsDirty[addressStart >> 7]) {
				addressEnd = addressStart + 40;

				resetHiresWords();
				calcNextHiresWords(addressStart);
				for (address = (addressStart + 2); address < addressEnd; address += 2) {
					bufferHiresWords();
					calcNextHiresWords(address);
					renderHiresBlock(displayOffset);
					displayOffset += DISPLAY_CHAR_SIZE_X * 4;
				}
				bufferHiresWords();
				renderHiresBlock(displayOffset);
				displayOffset += DISPLAY_CHAR_SIZE_X * 4;
				
				displayOffset += (DISPLAY_CHAR_SIZE_Y - 1) * DISPLAY_SIZE_X;
			} else
				displayOffset += DISPLAY_CHAR_SIZE_Y * DISPLAY_SIZE_X;
		}
	}

	/**
	 * Render double hires canvas
	 */
	private final void renderDoubleHiresWord(int destOffset, int hiresNibble) {
		displayImageBuffer[destOffset + 0] = doubleHiresPalette[(hiresNibble >> 0) & 0xf];
		displayImageBuffer[destOffset + 1] = doubleHiresPalette[(hiresNibble >> 4) & 0xf];
		displayImageBuffer[destOffset + 2] = doubleHiresPalette[(hiresNibble >> 8) & 0xf];
		displayImageBuffer[destOffset + 3] = doubleHiresPalette[(hiresNibble >> 12) & 0xf];
	}
	private final void renderDoubleHiresScanLine(int destOffset, int hiresWord) {
		renderDoubleHiresWord(destOffset + 0, hiresLookup[(hiresWord >> 0) & 0xff]);
		renderDoubleHiresWord(destOffset + 4, hiresLookup[(hiresWord >> 4) & 0xff]);
		renderDoubleHiresWord(destOffset + 8, hiresLookup[(hiresWord >> 8) & 0xff]);
		renderDoubleHiresWord(destOffset + 12, hiresLookup[(hiresWord >> 12) & 0xff]);
		renderDoubleHiresWord(destOffset + 16, hiresLookup[(hiresWord >> 16) & 0xff]);
		renderDoubleHiresWord(destOffset + 20, hiresLookup[(hiresWord >> 20) & 0xff]);
		renderDoubleHiresWord(destOffset + 24, hiresLookup[(hiresWord >> 24) & 0xff]);
	}
	private final void renderDoubleHiresBlock(int destOffset) {
		renderDoubleHiresScanLine(destOffset, hiresWord[0]); destOffset += DISPLAY_SIZE_X;
		renderDoubleHiresScanLine(destOffset, hiresWord[1]); destOffset += DISPLAY_SIZE_X;
		renderDoubleHiresScanLine(destOffset, hiresWord[2]); destOffset += DISPLAY_SIZE_X;
		renderDoubleHiresScanLine(destOffset, hiresWord[3]); destOffset += DISPLAY_SIZE_X;
		renderDoubleHiresScanLine(destOffset, hiresWord[4]); destOffset += DISPLAY_SIZE_X;
		renderDoubleHiresScanLine(destOffset, hiresWord[5]); destOffset += DISPLAY_SIZE_X;
		renderDoubleHiresScanLine(destOffset, hiresWord[6]); destOffset += DISPLAY_SIZE_X;
		renderDoubleHiresScanLine(destOffset, hiresWord[7]);
	}
	private final void calcNextDoubleHiresWord(int hiresWordIndex, int byte1, int byte2, int byte3, int byte4) {
		hiresWordNext[hiresWordIndex] = (
			((byte1 & 0x7f) << 2) | ((byte2 & 0x7f) << 9) | 
			((byte3 & 0x7f) << 16) | ((byte4 & 0x7f) << 23) |
			(hiresWord[hiresWordIndex] >> 28));
		hiresWord[hiresWordIndex] |= (hiresWordNext[hiresWordIndex] << 28);
	}
	private final void calcNextDoubleHiresWords(int address) {
		calcNextDoubleHiresWord(0, 
			apple.mem[address + 0x10000], apple.mem[address + 0x00000], 
			apple.mem[address + 0x10001], apple.mem[address + 0x00001]);
		calcNextDoubleHiresWord(1,
			apple.mem[address + 0x10400], apple.mem[address + 0x00400],
			apple.mem[address + 0x10401], apple.mem[address + 0x00401]);
		calcNextDoubleHiresWord(2,
			apple.mem[address + 0x10800], apple.mem[address + 0x00800],
			apple.mem[address + 0x10801], apple.mem[address + 0x00801]);
		calcNextDoubleHiresWord(3,
			apple.mem[address + 0x10c00], apple.mem[address + 0x00c00],
			apple.mem[address + 0x10c01], apple.mem[address + 0x00c01]);
		calcNextDoubleHiresWord(4,
			apple.mem[address + 0x11000], apple.mem[address + 0x01000],
			apple.mem[address + 0x11001], apple.mem[address + 0x01001]);
		calcNextDoubleHiresWord(5,
			apple.mem[address + 0x11400], apple.mem[address + 0x01400],
			apple.mem[address + 0x11401], apple.mem[address + 0x01401]);
		calcNextDoubleHiresWord(6,
			apple.mem[address + 0x11800], apple.mem[address + 0x01800],
			apple.mem[address + 0x11801], apple.mem[address + 0x01801]);
		calcNextDoubleHiresWord(7,
			apple.mem[address + 0x11c00], apple.mem[address + 0x01c00],
			apple.mem[address + 0x11c01], apple.mem[address + 0x01c01]);
	}	
	private void renderDoubleHires(int baseAddress, boolean isMixedMode) {
		int screenCharY, screenCharYEnd = isMixedMode ? 20 : 24;
		int displayOffset;
		int address, addressEnd, addressStart;
		
		displayOffset = 0;
		for (screenCharY = 0; screenCharY < screenCharYEnd; screenCharY++) {
			addressStart = baseAddress + textLineAddress[screenCharY];
			
			if (graphicsDirty[addressStart >> 7]) {
				addressEnd = addressStart + 40;

				resetHiresWords();
				calcNextDoubleHiresWords(addressStart);
				for (address = (addressStart + 2); address < addressEnd; address += 2) {
					bufferHiresWords();
					calcNextDoubleHiresWords(address);
					renderDoubleHiresBlock(displayOffset);
					displayOffset += DISPLAY_CHAR_SIZE_X * 4;
				}
				bufferHiresWords();
				renderDoubleHiresBlock(displayOffset);
				displayOffset += DISPLAY_CHAR_SIZE_X * 4;
				
				displayOffset += (DISPLAY_CHAR_SIZE_Y - 1) * DISPLAY_SIZE_X;
			} else
				displayOffset += DISPLAY_CHAR_SIZE_Y * DISPLAY_SIZE_X;
		}
	}
}
