
/**
 * AppleIIGo
 * The Java Apple II Emulator 
 * Copyright 2015 by Nick Westgate (Nick.Westgate@gmail.com)
 * Copyright 2006 by Marc S. Ressl (mressl@gmail.com)
 * Released under the GNU General Public License version 2 
 * See http://www.gnu.org/licenses/
 * 
 * Change list:
 * 
 * Version 1.0.10 - changes by Nick:
 * - fixed disk stepping bug for Mabel's Mansion using my code from AppleWin
 * - patch loaded ROM's empty slots with faux-floating bus data so Mabel's Mansion works
 * - revert CPU status bug introduced in 1.0.9 - V and R used the same bit
 * - fixed BRK bug by adding extra PC increment
 * - NOTE: decimal mode arithmetic fails some tests and should be fixed someday
 * 
 * Version 1.0.9 - changes by Nick:
 * - fixed disk speed-up bug (Sherwood Forest reads with the drive motor off)
 * - added check for 2IMG header ID
 * - fixed processor status bugs in BRK, PLP, RTI, NMI, IRQ 
 * 
 * Version 1.0.8 - changes by Nick:
 * - implemented disk writing (only in memory, not persisted)
 * - added support for .2MG (2IMG) disk images, including lock flag and volume number
 * - support meta tag for write protect in disk filename eg: NotWritable_Meta_DW0.dsk
 * 
 * Version 1.0.7 - changes by Nick:
 * - fixed disk emulation bug (sense write protect entered write mode)
 * - now honour diskWritable parameter (but writing is not implemented)
 * - support meta tag for volume number in disk filename eg: Vol2_Meta_DV2.dsk
 * - added isPaddleEnabled parameter
 * - exposed setPaddleEnabled(boolean value), setPaddleInverted(boolean value)
 * - paddle values are now 255 at startup (ie. correct if disabled/not present)
 * - minor AppleSpeaker fix (SourceDataLine.class) thanks to William Halliburton
 * 
 * Version 1.0.6 - changes by Nick:
 * - exposed F3/F4 disk swapping method: cycleDisk(int driveNumber)
 * - exposed reset() method 
 * - exposed setSpeed(int value) method
 * 
 * Version 1.0.5 - changes by Nick:
 * - added support for .NIB (nibble) disk images  (also inside ZIP archives)
 * - added disk speedup hacks for DOS (expect ~2x faster reads)
 * 
 * Version 1.0.4 - changes by Nick:
 * - added support for .PO (ProDOS order) disk images (also inside ZIP archives)
 * - added Command key for Closed-Apple on Mac OS X
 * - added Home and End keys for Open-Apple and Closed-Apple on full keyboards
 *
 * Version 1.0.3 - changes by Nick:
 * - fixed paddle values for scaled display window
 * - added "digital" joystick support via numeric keypad arrows
 * - added Left-Alt and Right-Alt keys for Open-Apple and Closed-Apple 
 * - changed reset key from Home to Ctrl-F12 and Ctrl-Pause/Break
 *
 * Version 1.0.2 - changes by Nick:
 * - improved sound sync by moving AppleSpeaker into the main thread
 * - added version (F1)
 * - added multiple disks & swapping (F3, F4)
 * - added ZIP archive support
 * - fixed HTTP disk image access bug
 */

import java.applet.Applet;
import java.awt.Graphics;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * AppleIIGo class<p>
 * Connects EmAppleII, AppleCanvas
 */
public class AppleIIGo extends Applet implements KeyListener, ComponentListener, 
	MouseListener, MouseMotionListener {

	private static final long serialVersionUID = -3302282815441501352L;
	
	final String version = "1.0.10";
	final String versionString = "AppleIIGo Version " + version;
	final String metaStart = "_meta_";

	// Class instances
	private EmAppleII apple;
	private AppleDisplay display;
	private DiskII disk;

	// Machine variables
	private boolean isCpuPaused;
	private boolean isCpuDebugEnabled;

	// Keyboard variables
	private boolean keyboardUppercaseOnly;

	// Paddle variables
	private boolean isPaddleEnabled;
	private boolean isPaddleInverted;
	
	// Disk variables - TODO: refactor into a class
	private String diskDriveResource[] = new String[2];
	private boolean diskWritable;
	private String[][] diskImageNames = {{}, {}};
	private int diskImageNumber[] = {0, 0};

	/**
 	 * Debug
	 */
	private void debug(String message) {
//		System.out.println(message);
	}

	/**
 	 * Parameters
	 */
	private String getAppletParameter(String parameter, String defaultValue) {
		String value = getParameter(parameter);
		if ((value == null) || (value.length() == 0))
			return defaultValue;
		return value;
	}

	/**
 	 * On applet initialization
	 */
	public void init() {
		debug("init()");

		// Activate listeners
		addKeyListener(this);
		addMouseListener(this);
		addMouseMotionListener(this);

		if (getAppletParameter("displayFocusOnStart", "true").equals("true"))
			addComponentListener(this);

		// Initialize Apple II emulator
		apple = new EmAppleII();
		loadRom(getAppletParameter("cpuRom", ""));
		apple.setCpuSpeed(new Integer(getAppletParameter("cpuSpeed", "1000")).intValue());
		isCpuPaused = getAppletParameter("cpuPaused", "false").equals("true");
		isCpuDebugEnabled = getAppletParameter("cpuDebugEnabled", "false").equals("true");
		apple.setStepMode(getAppletParameter("cpuStepMode", "false").equals("true"));
	
		// Keyboard
		keyboardUppercaseOnly = getAppletParameter("keyboardUppercaseOnly", "true").equals("true");

		// Paddles
		isPaddleEnabled = getAppletParameter("paddleEnabled", "true").equals("true");
		isPaddleInverted = getAppletParameter("paddleInverted", "false").equals("true");
		
		// Display
		display = new AppleDisplay(this, apple);
		display.setScale(new Float(getAppletParameter("displayScale", "1")).floatValue());
		display.setRefreshRate(new Integer(getAppletParameter("displayRefreshRate", "10")).intValue());
		display.setColorMode(new Integer(getAppletParameter("displayColorMode", "0")).intValue());
		display.setStatMode(getAppletParameter("displayStatMode", "false").equals("true"));
		display.setGlare(getAppletParameter("displayGlare", "false").equals("true"));

		// Speaker
		apple.speaker = new AppleSpeaker(apple);
		apple.speaker.setVolume(new Integer(getAppletParameter("speakerVolume", "6")).intValue());
		
		// Peripherals
		disk = new DiskII(apple);
		apple.setPeripheral(disk, 6);

		// Initialize disk drives
		diskWritable = getAppletParameter("diskWritable", "false").equals("true");
		diskImageNames[0] = getAppletParameter("diskDrive1", "").split("[|]");
		diskImageNumber[0] = 0;
		diskImageNames[1] = getAppletParameter("diskDrive2", "").split("[|]");
		diskImageNumber[1] = 0;
		mountDisk(0, diskImageNames[0][diskImageNumber[0]]);
		mountDisk(1, diskImageNames[1][diskImageNumber[1]]);

		// Start CPU
		if (!isCpuPaused)
			resume();
	}

	/**
 	 * Start applet
	 */
	public void start() {
		debug("AppleIIGo Version " + version);
		debug("start()");
	}

	/**
 	 * Stop applet
	 */
	public void stop() {
		debug("stop()");
	}

	/**
 	 * On applet destruction
	 */
	public void destroy() {
		debug("destroy()");
		unmountDisk(0);
		unmountDisk(1);
	}

	// Public Java interface

	/**
 	 * Javascript interface
	 */
	public void focus() {
		debug("focus()");
		requestFocus();
	}

	/**
 	 * Pause emulator
	 */
	public void pause() {
		debug("pause()");
		isCpuPaused = true;
		apple.setPaused(true);
		display.setPaused(true);
		apple.speaker.setPaused(true);
	}

	/**
 	 * Resume emulator
	 */
	public void resume() {
		debug("resume()");
		isCpuPaused = false;
		apple.speaker.setPaused(false);
		display.setPaused(false);
		apple.setPaused(false);
	}

	/**
 	 * Restarts emulator
	 */
	public void restart() {
		debug("restart()");
		apple.restart();
	}

	public void reset() {
		debug("reset()");
		apple.reset();
	}

	public void setSpeed(int value) {
		debug("setSpeed(" + value + ")");
		try
		{
			pause();
			this.wait(1000);
		}
		catch (Throwable e)
		{
		}
		apple.setCpuSpeed(value);
		resume();
	}

	public void cycleDisk(int driveNumber)
	{
		debug("cycleDisk(" + driveNumber + ")");
		if (diskImageNames[driveNumber].length > 1) {
			diskImageNumber[driveNumber] = ++diskImageNumber[driveNumber] % diskImageNames[driveNumber].length;
			mountDisk(driveNumber, diskImageNames[driveNumber][diskImageNumber[driveNumber]]);
		}
	}
	
	/**
	 * Open input stream
	 */
	private DataInputStream openInputStream(String resource) {
		return openInputStream(resource, null);
	}

	private DataInputStream openInputStream(String resource, StringBuffer OutFilename) {
		InputStream is = null;

		if (OutFilename != null)
		{
			OutFilename.setLength(0);
			int slashPos = resource.lastIndexOf('/');
			int backslashPos = resource.lastIndexOf('\\');
			int index = Math.max(slashPos, backslashPos);
			OutFilename.append(resource.substring((index > 0) ? index : 0));
		}

		try {
			URL codeBase = getCodeBase();
			URL url = new URL(codeBase, resource);
			debug("resource: " + url.toString());

			is = url.openStream();
			if (resource.toLowerCase().endsWith(".gz"))
			{
				is = new GZIPInputStream(is);
			}
			else if (resource.toLowerCase().endsWith(".zip"))
			{
				is = new ZipInputStream(is);
				ZipEntry entry = ((ZipInputStream)is).getNextEntry();
				if (OutFilename != null)
				{
					OutFilename.setLength(0);
					OutFilename.append(entry.getName());
				}
			}
		} catch (Exception e) {
			debug("Exeption: " + e.getLocalizedMessage());
		}
		
		if (is == null)
		{
			debug("failed");
			return null;
		}
		else
		{
			debug("ok");
			return new DataInputStream(is);
		}
	}

	/**
	 * Open output stream
	 */
	private OutputStream openOutputStream(String resource) {
		OutputStream os = null;
		
		try {
			if (!(resource.substring(0, 6).equals("http://")))
				os = new FileOutputStream(resource);
		} catch (Exception e) {
		}
		
		return os;
	}

	/**
	 * Load ROM
	 */
	public boolean loadRom(String resource) {
		debug("loadRom(resource: " + resource + ")");
		boolean success = false;
		
		try {
			DataInputStream is = openInputStream(resource);
			success = apple.loadRom(is);
			is.close();
		} catch (Exception e) {
			debug("Exeption: " + e.getLocalizedMessage());
		}
		
		return success;
	}
	
	/**
 	 * Mount a disk
	 */
	public boolean mountDisk(int drive, String resource) {
		debug("mountDisk(drive: " + drive + ", resource: " + resource + ")");
		boolean success = false;

		if ((drive < 0) || (drive > 1))
			return success;
			
		try {
			unmountDisk(drive);

			diskDriveResource[drive] = resource;

			StringBuffer diskname = new StringBuffer();
			DataInputStream is = openInputStream(resource, diskname);
			
			int diskVolumeNumber = DiskII.DEFAULT_VOLUME;
			boolean diskWritableOverride = diskWritable;

			// handle disk meta tag for disk volume (etc?)
			// could break this out into a method, but then multiple tags ...?
			String lowerDiskname = diskname.toString().toLowerCase();
			int metaIndex = lowerDiskname.indexOf(metaStart);
			if (metaIndex != -1)
			{
				metaIndex += metaStart.length();
				int command = 0;
				int operand = 0;
				boolean execute = false;
				while (metaIndex < lowerDiskname.length())
				{
					char c = lowerDiskname.charAt(metaIndex++);
					switch (c)
					{
						case '0': case '1':case '2':case '3':case '4':
						case '5': case '6':case '7':case '8':case '9':
						{
							operand = 10 * operand + (c - '0');
							break;
						}
						
						case '.': // end meta
							metaIndex = lowerDiskname.length();
							execute = true;
							break;

						case '_': // end word
							execute = true;
							break;
							
						default:
						{
							if (c >= 'a' && c <= 'z')
							{
								command = (command << 16) + c;
								execute = (command & 0xFFFF0000) != 0;
							}
							break;
						}
					}
					if (execute)
					{
						switch (command)
						{
							case ('d' << 16) + 'v':
								diskVolumeNumber = operand;
								break;

							case ('d' << 16) + 'w':
								diskWritableOverride = (operand != 0);
								break;
						}
						command = 0;
						operand = 0;
					}
				}
			}
			
			success = disk.readDisk(drive, is, diskname.toString(), !diskWritableOverride, diskVolumeNumber);
			is.close();
			showStatus("Drive " + (drive + 1) + ": " + resource);
		} catch (Exception e) {
			debug("Exeption: " + e.getLocalizedMessage());
		}
		
		return success;
	}

	/**
 	 * Unmount a disk
	 */
	public void unmountDisk(int drive) {
		debug("unmountDisk(drive: " + drive + ")");
		if ((drive < 0) || (drive > 1))
			return;

		if (!diskWritable)
			return;
			
		// TODO: only for local disk cache when it's working
		//try {
			//OutputStream os = openOutputStream(diskDriveResource[drive]);
			//disk.writeDisk(drive, os);
			//os.close();
		//} catch (Exception e) {
		//}
	}

	/**
 	 * Set color mode
	 */
	public void setColorMode(int value) {
		debug("setColorMode(value: " + value + ")");
		display.setColorMode(value);
	}

	/**
 	 * Set paddle enabled/disabled
	 */
	public void setPaddleEnabled(boolean value) {
		debug("setPaddleEnabled(value: " + value + ")");
		isPaddleEnabled = value;
		if (!value)
		{
			apple.paddle.setPaddlePos(0, Paddle.PADDLE_HIGH);
			apple.paddle.setPaddlePos(1, Paddle.PADDLE_HIGH);
			apple.paddle.setButton(0, false);
			apple.paddle.setButton(1, false);
		}
	}

	/**
 	 * Set paddle inverted/normal
	 */
	public void setPaddleInverted(boolean value) {
		debug("setPaddleInverted(value: " + value + ")");
		isPaddleInverted = value;
	}

	/**
 	 * Get disk activity
	 */
	public boolean getDiskActivity() {
		return (!isCpuPaused && disk.isMotorOn());
	}

	public int getSizeX()
	{
		return display.getSizeX();
	}

	public int getSizeY()
	{
		return display.getSizeY();
	}

	/**
 	 * KeyListener event handling
	 */	
	public void keyTyped(KeyEvent e) {
		// Send to emulator
		int key = e.getKeyChar();
		if (key == 10)
			apple.setKeyLatch(13);
		else if (key < 128) {
			if (keyboardUppercaseOnly && (key >= 97) && (key <= 122))
				key -= 32;
			
			apple.setKeyLatch(key);
		}
	}

    public void keyPressed(KeyEvent e) {
		switch(e.getKeyCode()) {
			
		case KeyEvent.VK_META:
			apple.paddle.setButton(1, true);
			break;
		case KeyEvent.VK_ALT:
			if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_LEFT)
			{
				apple.paddle.setButton(0, true);
			}
			else if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT)
			{
				apple.paddle.setButton(1, true);
			}
			break;
		case KeyEvent.VK_BACK_SPACE:
		case KeyEvent.VK_LEFT:
			if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD)
			{
				handleKeypadLeft();
			}
			else
			{
				apple.setKeyLatch(8);
			}
			break;
		case KeyEvent.VK_RIGHT:
			if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD)
			{
				handleKeypadRight();
			}
			else
			{
				apple.setKeyLatch(21);
			}
			break;
		case KeyEvent.VK_UP:
			if (e.isControlDown())
			{
				apple.speaker.setVolume(apple.speaker.getVolume() + 1);
			}
			else if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD)
			{
				handleKeypadUp();
			}
			else
			{
				apple.setKeyLatch(11);
			}
			break;
		case KeyEvent.VK_DOWN:
			if (e.isControlDown())
			{
				apple.speaker.setVolume(apple.speaker.getVolume() - 1);
			}
			else if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD)
			{
				handleKeypadDown();
			}
			else
			{
				apple.setKeyLatch(10);
			}
			break;
		case KeyEvent.VK_ESCAPE:
			apple.setKeyLatch(27);
			break;
		case KeyEvent.VK_DELETE:
			apple.setKeyLatch(127);
			break;
		case KeyEvent.VK_HOME:
			if (e.isControlDown())
				restart();
			else
				apple.paddle.setButton(0, true);
			break;
		case KeyEvent.VK_END:
			apple.paddle.setButton(1, true);
			break;
		case KeyEvent.VK_F1:
			showStatus("AppleIIGo Version " + version);
			break;
		case KeyEvent.VK_F3:
			cycleDisk(0);
			break;
		case KeyEvent.VK_F4:
			cycleDisk(1);
			break;
		case KeyEvent.VK_F5:
			if (isCpuDebugEnabled)
				display.setStatMode(!display.getStatMode());
			break;
		case KeyEvent.VK_F6:
			if (isCpuDebugEnabled)
				apple.setStepMode(!apple.getStepMode());
			break;
		case KeyEvent.VK_F7:
			if (isCpuDebugEnabled) {
				apple.setStepMode(apple.getStepMode());
				apple.stepInstructions(1);
			}
			break;
		case KeyEvent.VK_F8:
			if (isCpuDebugEnabled) {
				apple.setStepMode(apple.getStepMode());
				apple.stepInstructions(128);
			}
			break;
		case KeyEvent.VK_CANCEL: // Pause/Break sends this (as Mac OS swallows Ctrl-F12)
		case KeyEvent.VK_F12:
			if (e.isControlDown())
				reset();
			break;
		case KeyEvent.VK_KP_LEFT:
			handleKeypadLeft();
			break;
		case KeyEvent.VK_KP_RIGHT:
			handleKeypadRight();
			break;
		case KeyEvent.VK_KP_UP:
			handleKeypadUp();
			break;
		case KeyEvent.VK_KP_DOWN:
			handleKeypadDown();
			break;
		}
    }

    public void keyReleased(KeyEvent e) {
		switch(e.getKeyCode()) {
		case KeyEvent.VK_META:
			apple.paddle.setButton(1, false);
			break;
		case KeyEvent.VK_ALT:
			if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_LEFT)
			{
				apple.paddle.setButton(0, false);
			}
			else if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT)
			{
				apple.paddle.setButton(1, false);
			}
			break;
		case KeyEvent.VK_LEFT:
		case KeyEvent.VK_RIGHT:
			if (e.getKeyLocation() != KeyEvent.KEY_LOCATION_NUMPAD)
				break;
			// else fall through
		case KeyEvent.VK_KP_LEFT:
		case KeyEvent.VK_KP_RIGHT:
			handleKeypadCentreX();
			break;
		case KeyEvent.VK_UP:
		case KeyEvent.VK_DOWN:
			if (e.getKeyLocation() != KeyEvent.KEY_LOCATION_NUMPAD)
				break;
			// else fall through
		case KeyEvent.VK_KP_UP:
		case KeyEvent.VK_KP_DOWN:
			handleKeypadCentreY();
			break;
		case KeyEvent.VK_HOME:
			if (!e.isControlDown())
			{
				apple.paddle.setButton(0, false);
			}
		case KeyEvent.VK_END:
			apple.paddle.setButton(1, false);
		}
    }

	private void handleKeypadCentreX() {
		if (isPaddleEnabled)
		{
			if (isPaddleInverted) {
				apple.paddle.setPaddlePos(1, 127);
			} else {
				apple.paddle.setPaddlePos(0, 127);
			}
		}
	}

	private void handleKeypadCentreY() {
		if (isPaddleEnabled)
		{
			if (isPaddleInverted) {
				apple.paddle.setPaddlePos(0, 127);
			} else {
				apple.paddle.setPaddlePos(1, 127);
			}
		}
	}

	private void handleKeypadLeft() {
		if (isPaddleEnabled)
		{
			if (isPaddleInverted) {
				apple.paddle.setPaddlePos(1, 255);
			} else {
				apple.paddle.setPaddlePos(0, 0);
			}
		}
	}

	private void handleKeypadRight() {
		if (isPaddleEnabled)
		{
			if (isPaddleInverted) {
				apple.paddle.setPaddlePos(1, 0);
			} else {
				apple.paddle.setPaddlePos(0, 255);
			}
		}
	}

	private void handleKeypadUp() {
		if (isPaddleEnabled)
		{
			if (isPaddleInverted) {
				apple.paddle.setPaddlePos(0, 255);
			} else {
				apple.paddle.setPaddlePos(1, 0);
			}
		}
	}

	private void handleKeypadDown() {
		if (isPaddleEnabled)
		{
			if (isPaddleInverted) {
				apple.paddle.setPaddlePos(0, 0);
			} else {
				apple.paddle.setPaddlePos(1, 255);
			}
		}
	}

	/**
 	 * ComponentListener event handling
	 */	
	public void componentHidden(ComponentEvent e) {
	}

	public void componentMoved(ComponentEvent e) {
	}

	public void componentResized(ComponentEvent e) {
	}

	public void componentShown(ComponentEvent e) {
		debug("componentShown()");

		removeComponentListener(this);
		requestFocus();
	}

	/**
 	 * MouseListener, MouseMotionListener event handling
	 */	
	public void mouseClicked(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
		try {
			getAppletContext().showDocument(new URL("javascript:flipMouseOver();"));
		} catch (MalformedURLException ex) {
		}
	}

	public void mouseExited(MouseEvent e) {
		try {
			getAppletContext().showDocument(new URL("javascript:flipMouseOut();"));
		} catch (MalformedURLException ex) {
		}
	}

	public void mousePressed(MouseEvent e) {
		int modifiers = e.getModifiers();
		if (isPaddleEnabled)
		{
			if ((modifiers & InputEvent.BUTTON1_MASK) != 0)
				apple.paddle.setButton(0, true);
			if ((modifiers & InputEvent.BUTTON3_MASK) != 0)
				apple.paddle.setButton(1, true);
		}
	}

	public void mouseReleased(MouseEvent e) {
		int modifiers = e.getModifiers();
		if (isPaddleEnabled)
		{
			if ((modifiers & InputEvent.BUTTON1_MASK) != 0)
				apple.paddle.setButton(0, false);
			if ((modifiers & InputEvent.BUTTON3_MASK) != 0)
				apple.paddle.setButton(1, false);
		}
	}

	public void mouseDragged(MouseEvent e) {
		mouseMoved(e);
	}

	public void mouseMoved(MouseEvent e) {
		float scale = display.getScale();
		if (isPaddleEnabled)
		{
			if (isPaddleInverted) {
				apple.paddle.setPaddlePos(0, (int) (255 - e.getY() * 256 / (192 * scale)));
				apple.paddle.setPaddlePos(1, (int) (255 - e.getX() * 256 / (280 * scale)));
			} else {
				apple.paddle.setPaddlePos(0, (int) (e.getX() * 256 / (280 * scale)));
				apple.paddle.setPaddlePos(1, (int) (e.getY() * 256 / (192 * scale)));
			}
		}
	}

	/**
 	 * Applet paint function
	 */
	public void paint(Graphics g) {
		display.paint(g);
	}

	/**
 	 * Applet update function
	 */
	public void update(Graphics g) {
		display.paint(g);
	}
}
