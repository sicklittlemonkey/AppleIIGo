
/**
 * AppleIIGo
 * The Java Apple II Emulator 
 * (C) 2006 by Marc S. Ressl(ressl@lonetree.com)
 * Released under the GPL
 */

import java.applet.*;
import java.io.*;
import java.net.*;
import java.util.zip.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.Graphics2D;

/**
 * AppleIIGo class<p>
 * Connects EmAppleII, AppleCanvas
 */
public class AppleIIGo extends Applet implements KeyListener, ComponentListener, 
	MouseListener, MouseMotionListener {
	// Class instances
	private EmAppleII apple;
	private AppleDisplay display;
	private AppleSpeaker speaker;
	private DiskII disk;

	// Machine variables
	private boolean isCpuPaused;
	private boolean isCpuDebugEnabled;

	// Keyboard variables
	private boolean keyboardUppercaseOnly;

	// Paddle variables
	private boolean isPaddleInverted;
	
	// Disk variables
	private String diskDriveResource[] = new String[2];
	private boolean diskWritable;

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

		// Display
		display = new AppleDisplay(this, apple);
		display.setScale(new Float(getAppletParameter("displayScale", "1")).floatValue());
		display.setRefreshRate(new Integer(getAppletParameter("displayRefreshRate", "10")).intValue());
		display.setColorMode(new Integer(getAppletParameter("displayColorMode", "0")).intValue());
		display.setStatMode(getAppletParameter("displayStatMode", "false").equals("true"));
		display.setGlare(getAppletParameter("displayGlare", "false").equals("true"));

		// Speaker
		speaker = new AppleSpeaker(apple);
		speaker.setVolume(new Integer(getAppletParameter("speakerVolume", "3")).intValue());
		
		// Peripherals
		disk = new DiskII();
		apple.setPeripheral(disk, 6);

		// Initialize disk drives
		diskWritable = getAppletParameter("diskWritable", "false").equals("true");
		mountDisk(0, getAppletParameter("diskDrive1", ""));
		mountDisk(1, getAppletParameter("diskDrive2", ""));

		// Start CPU
		if (!isCpuPaused)
			resume();
	}

	/**
 	 * Start applet
	 */
	public void start() {
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
		apple.setPaused(isCpuPaused);
		display.setPaused(isCpuPaused);
//		speaker.setPaused(isCpuPaused);
	}

	/**
 	 * Resume emulator
	 */
	public void resume() {
		debug("resume()");
		isCpuPaused = false;
//		speaker.setPaused(isCpuPaused);
		display.setPaused(isCpuPaused);
		apple.setPaused(isCpuPaused);
	}

	/**
 	 * Restarts emulator
	 */
	public void restart() {
		debug("restart()");
		apple.restart();
	}
	
	/**
	 * Open input stream
	 */
	private InputStream openInputStream(String resource) {
		InputStream is = null;
		
		try {
			URL url = new URL(getCodeBase(), resource);
			is = url.openStream();

			if (resource.toLowerCase().endsWith(".gz"))
				is = new GZIPInputStream(is);
		} catch (Exception e) {
		}
		
		return is;
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
			InputStream is = openInputStream(resource);
			success = apple.loadRom(is);
			is.close();
		} catch (Exception e) {
		}
		
		return success;
	}
	
	/**
 	 * Mount a disk
	 */
	public boolean mountDisk(int drive, String resource) {
		debug("mountDisk(drive: " + drive + ", resource: " + resource + ")");
		boolean success = false;

		if ((drive < 0) || (drive > 2))
			return success;
			
		try {
			unmountDisk(drive);

			diskDriveResource[drive] = resource;

			InputStream is = openInputStream(resource);
			success = disk.readDisk(drive, is, 254, false);
			is.close();
		} catch (Exception e) {
		}
		
		return success;
	}

	/**
 	 * Unmount a disk
	 */
	public void unmountDisk(int drive) {
		debug("unmountDisk(drive: " + drive + ")");
		if ((drive < 0) || (drive > 2))
			return;

		if (!diskWritable)
			return;
			
		try {
			OutputStream os = openOutputStream(diskDriveResource[drive]);
			disk.writeDisk(drive, os);
			os.close();
		} catch (Exception e) {
		}
	}

	/**
 	 * Set color mode
	 */
	public void setColorMode(int value) {
		debug("setColorMode(value: " + value + ")");
		display.setColorMode(value);
	}

	/**
 	 * Get disk activity
	 */
	public boolean getDiskActivity() {
		return (!isCpuPaused && disk.isMotorOn());
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
		case KeyEvent.VK_BACK_SPACE:
		case KeyEvent.VK_LEFT:
			apple.setKeyLatch(8);
			break;
		case KeyEvent.VK_RIGHT:
			apple.setKeyLatch(21);
			break;
		case KeyEvent.VK_UP:
			if (e.isControlDown())
				speaker.setVolume(speaker.getVolume() + 1);
			else
				apple.setKeyLatch(11);
			break;
		case KeyEvent.VK_DOWN:
			if (e.isControlDown())
				speaker.setVolume(speaker.getVolume() - 1);
			else
				apple.setKeyLatch(10);
			break;
		case KeyEvent.VK_ESCAPE:
			apple.setKeyLatch(27);
			break;
		case KeyEvent.VK_DELETE:
			apple.setKeyLatch(127);
			break;
		case KeyEvent.VK_HOME:
			if (e.isControlDown())
				apple.restart();
			else
				apple.reset();
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
		}
    }

    public void keyReleased(KeyEvent e) {
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
		
		if ((modifiers & InputEvent.BUTTON1_MASK) != 0)
			apple.paddle.setButton(0, true);
		if ((modifiers & InputEvent.BUTTON3_MASK) != 0)
			apple.paddle.setButton(1, true);
	}

	public void mouseReleased(MouseEvent e) {
		int modifiers = e.getModifiers();
		
		if ((modifiers & InputEvent.BUTTON1_MASK) != 0)
			apple.paddle.setButton(0, false);
		if ((modifiers & InputEvent.BUTTON3_MASK) != 0)
			apple.paddle.setButton(1, false);
	}

	public void mouseDragged(MouseEvent e) {
		mouseMoved(e);
	}

	public void mouseMoved(MouseEvent e) {
		if (isPaddleInverted) {
			apple.paddle.setPaddlePos(0, (int) (display.getScale() * (255 - e.getY() * 256 / 192)));
			apple.paddle.setPaddlePos(1, (int) (display.getScale() * (255 - e.getX() * 256 / 280)));
		} else {
			apple.paddle.setPaddlePos(0, (int) (e.getX() * display.getScale() * 256 / 280));
			apple.paddle.setPaddlePos(1, (int) (e.getY() * display.getScale() * 256 / 192));
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
