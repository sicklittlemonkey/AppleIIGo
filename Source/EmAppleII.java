
/**
 * AppleIIGo
 * Apple II Emulator for J2ME
 * Copyright 2006 by Marc S. Ressl(mressl@gmail.com)
 * Released under the GPL
 */

import java.io.*;

public class EmAppleII extends Em6502 implements Runnable {
	/*
	* Apple II memory map
	*/
	public static final int MEM_PHYS_ZP =		0x00000;
	public static final int MEM_PHYS_STACK =	0x00100;
	public static final int MEM_PHYS_RAM1 =		0x00200;
	public static final int MEM_PHYS_TEXT =		0x00400;
	public static final int MEM_PHYS_RAM2 =		0x00800;
	public static final int MEM_PHYS_HIRES =	0x02000;
	public static final int MEM_PHYS_RAM3 =		0x04000;
	public static final int MEM_PHYS_IO =		0x0c000;
	public static final int MEM_PHYS_ROM_LOW =	0x0d000;
	public static final int MEM_PHYS_ROM_HIGH =	0x0e000;

	public static final int MEM_MAIN_RAM1 =		0x00200;
	public static final int MEM_MAIN_TEXT =		0x00400;
	public static final int MEM_MAIN_RAM2 =		0x00800;
	public static final int MEM_MAIN_HIRES =	0x02000;
	public static final int MEM_MAIN_RAM3 =		0x04000;
	public static final int MEM_MAIN_LC1 =		0x0c000;
	public static final int MEM_MAIN_LC2 =		0x0d000;
	public static final int MEM_MAIN_LC_HIGH =	0x0e000;

	public static final int MEM_AUX_ZP =		0x10000;
	public static final int MEM_AUX_STACK =		0x10100;
	public static final int MEM_AUX_RAM1 =		0x10200;
	public static final int MEM_AUX_TEXT =		0x10400;
	public static final int MEM_AUX_RAM2 =		0x10800;
	public static final int MEM_AUX_HIRES =		0x12000;
	public static final int MEM_AUX_RAM3 =		0x14000;
	public static final int MEM_AUX_LC1 =		0x1c000;
	public static final int MEM_AUX_LC2 =		0x1d000;
	public static final int MEM_AUX_LC_HIGH =	0x1e000;

	public static final int MEM_ROM_MAIN_LOW =	0x20000;
	public static final int MEM_ROM_MAIN_HIGH =	0x21000;
	public static final int MEM_ROM_INTERNAL =	0x23000;
	public static final int MEM_ROM_EXTERNAL =	0x24000;

	public static final int MEM_MAIN_ZP =		0x25000;
	public static final int MEM_MAIN_STACK =	0x25100;

	public static final int MEM_WASTE =			0x25200;

	public static final int MEM_END =			0x28000;
	
	// Peripherals
	public Paddle paddle;
	public Peripheral[] slots;
	public AppleSpeaker speaker;
	
	// Graphics	(dirty buffer every 0x80 bytes)
	public int graphicsMode;
	public boolean[] graphicsDirty = new boolean[0x10000 >> 7];
	
	public static final int GR_TEXT		= (1 << 0);
	public static final int GR_MIXMODE	= (1 << 1);
	public static final int GR_PAGE2	= (1 << 2);
	public static final int GR_HIRES	= (1 << 3);
	public static final int GR_80STORE	= (1 << 4);
	public static final int GR_80CHAR	= (1 << 5);
	public static final int GR_ALTCHAR	= (1 << 6);
	public static final int GR_DHIRES	= (1 << 7);

	// Sound
	public static final int SPEAKER_FLIPS_BITS = 12;
	public static final int SPEAKER_FLIPS_SIZE = 1 << SPEAKER_FLIPS_BITS;
	public static final int SPEAKER_FLIPS_MASK = SPEAKER_FLIPS_SIZE - 1;
	
	public int speakerFlips[] = new int[SPEAKER_FLIPS_SIZE];
	public int speakerFlipsPointer = 0;

	// Default ROM
	private static final int[] defaultRom = {
		// Reset routine
		0xad,0x51,0xc0,
		0xa9,0xa0,
		
		0xa2,0xff,0x9d,0xff,0x03,0xca,0xd0,0xfa,
		0xa2,0xff,0x9d,0xff,0x04,0xca,0xd0,0xfa,
		0xa2,0xff,0x9d,0xff,0x05,0xca,0xd0,0xfa,
		0xa2,0xff,0x9d,0xff,0x06,0xca,0xd0,0xfa,
			
		0xa2,0x27,0xbd,0xb8,0xfe,0x9d,0x80,0x04,0xca,0x10,0xf7,
		0xa2,0x27,0xbd,0xe0,0xfe,0x9d,0x00,0x05,0xca,0x10,0xf7,
		0xa2,0x27,0xbd,0x08,0xff,0x9d,0x80,0x05,0xca,0x10,0xf7,
		0xa2,0x27,0xbd,0x30,0xff,0x9d,0x00,0x06,0xca,0x10,0xf7,
		0xa2,0x27,0xbd,0x58,0xff,0x9d,0x80,0x07,0xca,0x10,0xf7,
		0xa2,0x27,0xbd,0x80,0xff,0x9d,0x28,0x04,0xca,0x10,0xf7,
		0xa2,0x27,0xbd,0xa8,0xff,0x9d,0x00,0x07,0xca,0x10,0xf7,
		0xa2,0x27,0xbd,0xd0,0xff,0x9d,0x00,0x07,0xca,0x10,0xf7,

		0x4c,0xad,0xfe,
		0xea,0xea,0xea,0xea,0xea,0xea,0xea,0xea,
		
		// Text message
		0xa0,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,
		0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,
		0x20,0x20,0x20,0x20,0x20,0x20,0x20,0xa0,

		0xa0,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x01,0x10,0x10,0x0c,0x05,0x09,0x09,
		0x07,0x0f,0x20,0x12,0x05,0x11,0x15,0x09,0x12,0x05,0x13,0x20,0x01,0x0e,0x20,0x20,
		0x20,0x20,0x20,0x20,0x20,0x20,0x20,0xa0,	// APPLEIIGO REQUIRES AN
		
		0xa0,0x20,0x20,0x20,0x20,0x20,0x20,0x01,0x10,0x10,0x0c,0x05,0x20,0x09,0x09,0x20,
		0x12,0x0f,0x0d,0x20,0x09,0x0d,0x01,0x07,0x05,0x20,0x14,0x0f,0x20,0x12,0x15,0x0e,
		0x20,0x20,0x20,0x20,0x20,0x20,0x20,0xa0,	// APPLE II ROM IMAGE TO RUN

		0xa0,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,
		0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,
		0x20,0x20,0x20,0x20,0x20,0x20,0x20,0xa0,

		0xa0,0xc6,0xcf,0xd2,0xa0,0xcd,0xcf,0xd2,0xc5,0xa0,0xc9,0xce,0xc6,0xcf,0xd2,0xcd,
		0xc1,0xd4,0xc9,0xcf,0xce,0xa0,0xd0,0xcc,0xc5,0xc1,0xd3,0xc5,0xa0,0xc3,0xcc,0xc9,
		0xc3,0xcb,0xa0,0xcf,0xce,0xa0,0xa0,0xa0,	// FOR MORE INFORMATION PLEASE CLICK ON

		0xa0,0xd4,0xc8,0xc5,0xa0,0xc1,0xd0,0xd0,0xcc,0xc5,0xc9,0xc9,0xc7,0xcf,0xa0,0xcc,
		0xcf,0xc7,0xcf,0xa0,0xc2,0xc5,0xcc,0xcf,0xd7,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,
		0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,	// THE APPLEIIGO LOGO BELOW

		0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,
		0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,
		0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,

		0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,
		0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,
		0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,0xa0,

		// Interrupt vectors
		0x00,0x00,0x30,0xfe,0x30,0xfe,0x30,0xfe		
	};

	// Emulator
	private boolean isRestart;

	private int cpuSpeed;
	private int clocksPerInterval;

	private int refreshRate;
	private long refreshInterval;
	private long refreshDelayCumulative;
	private long refreshDelayPerSecond;
	private long refreshCycle;
	
	// Keyboard
	private int keyboardLatch;

	// Memory offsets
	private int[] memoryReadOffset = new int[0x101];
	private int[] memoryWriteOffset = new int[0x101];

	// Language card state
	private boolean isLcReadEnable;
	private boolean isLcWriteEnable;
	private boolean isLcBank2;
	
	// Apple IIe state
	private boolean isRomInternal;
	private boolean isRomC3External;
	private boolean isAuxRead;
	private boolean isAuxWrite;
	private boolean isAuxZeroPage;
	private boolean isVideoVBL;

	// Thread stuff
	private boolean isPaused = true;
	private Thread thread;
	private String threadError = null;
	
	// Step mode
	private boolean isStepMode = false;
	private boolean isNextStep = false;
	private int stepCount;


	
	/**
 	 * Apple II class constructor
	 */
	public EmAppleII() {
		super();
		
		// Allocate compute memory
		mem = new byte[MEM_END];

		// Initialize CPU
		initMemoryMap();
		setRandomSeed();
		setCpuSpeed(1000);
		reset();

		// Setup default ROM
		loadDefaultRom();
		
		// Setup paddles
		paddle = new Paddle(this);

		// Setup expansion slots
		slots = new Peripheral[8];
		for (int slot = 1; slot < 8; slot++)
			setPeripheral(new Peripheral(), slot);
	}
	
	/**
	 * Set random seed (so programs start randomly)
	 */
	public void setRandomSeed() {
		mem[0xcd] = (byte) System.currentTimeMillis();
	}
	
	/**
	 * Load default ROM
	 */
    public void loadDefaultRom() {
		for(int offset = 0; offset < 0x1d0; offset++)
			mem[(MEM_ROM_MAIN_LOW + 0x3000 - 0x1d0) + offset] = (byte) defaultRom[offset];
	}

	/**
 	 * Loads ROM
	 */
	private boolean isValidRom(byte[] rom, int offset) {
		// Integer BASIC?
		if ((rom[offset + 0x1000] & 0xff) == 0x20)
			return true;

		// Applesoft BASIC?
		if ((rom[offset + 0x1000] & 0xff) == 0x4c)
			return true;

		return false;
	}
	
	public boolean loadRom(DataInputStream is) throws IOException {
		byte[] rom = new byte[0x8000];
		int offset = 0;
				
		is.readFully(rom, 0, 0x08000);
		
		if (isValidRom(rom, 0x0))
			offset = 0x0;
		else if (isValidRom(rom, 0x1000))
			offset = 0x1000;
		else if (isValidRom(rom, 0x2000))
			offset = 0x2000;
		else
			return false;
			
		// Copy main ROM
		System.arraycopy(rom, offset, mem, MEM_ROM_MAIN_LOW, 0x03000);

		// Copy internal ROM
		System.arraycopy(rom, offset + 0x3000, mem, MEM_ROM_INTERNAL + 0x00000, 0x01000);
		System.arraycopy(rom, offset + 0x3800, mem, MEM_ROM_EXTERNAL + 0x00800, 0x00800);

		for (int slot = 0x100; slot <= 0x700; slot += 0x100)
		{
			if (mem[MEM_ROM_EXTERNAL + slot] == 0)
			{
				// 0 data is a bad default for empty external slots (e.g. Mabel's Mansion reboots)
				// so ideally we would emulate the floating bus, but for now we just hardcode 0xA0
				for (int i = 0; i <= 0xFF; i++)
					mem[MEM_ROM_EXTERNAL + slot + i] = (byte)0xA0;
			}
		}

		return true;
	}

	/**
 	 * Set peripheral
	 */
	public void setPeripheral(Peripheral peripheral, int slot) {
		slots[slot] = peripheral;
		
		int offset = MEM_ROM_EXTERNAL + (slot << 8);
		for(int i = 0; i < 0x100; i++)
			mem[offset + i] = (byte) peripheral.memoryRead(i);
	}

	/**
 	 * Set pause state
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
		} else {
			thread = new Thread(this);
			thread.start();
		}
	}
	
	/**
 	 * Get pause state
	 */
	public boolean getPaused() {
		return isPaused;
	}

	/**
 	 * Keyboard push key function
	 */
	public void setKeyLatch(int key) {
		key &= 0x7f;
		keyboardLatch = (key | 0x80);
	}

	/**
	 * Restart
	 */
	public void restart() {
		isRestart = true;
		assertReset();
	}

	/**
	 * Reset
	 */
	public void reset() {
		isRestart = false;
		assertReset();
	}

	/**
 	 * Set CPU speed
	 */
	public void setCpuSpeed(int value) {
		if (value < 0)
			return;
			
		cpuSpeed = value;

		refreshRate = 20;
		refreshInterval = (int) (1000.0 / refreshRate);
		clocksPerInterval = (int) (cpuSpeed * refreshInterval);
	}

	/**
 	 * Get debug mode
	 */
	public int getCpuSpeed() {
		return cpuSpeed;
	}

	/**
 	 * Get refresh rate
	 */
	public int getRefreshRate() {
		return refreshRate;
	}

	/**
 	 * Set debug mode
	 */
	public void setStepMode(boolean value) {
		isNextStep = false;
		isStepMode = value;
	}

	/**
 	 * Get debug mode
	 */
	public boolean getStepMode() {
		return isStepMode;
	}

	/**
 	 * Step instruction in debug mode
	 */
	public void stepInstructions(int value) {
		if (value <= 0)
			return;
		
		stepCount = value;
		isNextStep = true;
	}
	
	/**
	 * Zero pad
	 */
	private String zeroPad(String value, int length) {
		length -= value.length();
		
		while (length > 0) {
			value = "0" + value;
			length--;
		}
		
		return value;
	}

	/**
	 * Print a hex value
	 */
	private String formatHex(int value, int length) {
		return zeroPad(Integer.toString(value, 16), length);
	}

	/**
	 * Print a decimal value
	 */
	private String formatDec(int value, int commaPos) {
		String valueString = zeroPad(Integer.toString(value), commaPos + 1);
		int length = valueString.length();

		return valueString.substring(0, length - commaPos) +
			"." + valueString.substring(length - commaPos, length);
	}

	/**
 	 * Step instruction in debug mode
	 */
	public String getStatInfo() {
		String statInfo = "";
		long cpuSpeedCurrent;

		// Calculate effective CPU speed
		if (isPaused || isStepMode)
			cpuSpeedCurrent = 0;
		else if (refreshDelayPerSecond > 1000)
			cpuSpeedCurrent = cpuSpeed * 1000 / refreshDelayPerSecond;
		else
			cpuSpeedCurrent = cpuSpeed;

		// Return A, X, Y, S, P, PC
		statInfo += " A=" + formatHex(A, 2);
		statInfo += " X=" + formatHex(X, 2);
		statInfo += " Y=" + formatHex(Y, 2);
		statInfo += " P=" + formatHex(P, 2);
		statInfo += " S=" + formatHex(S, 2);
		statInfo += "\n";
		statInfo += " PC=" + formatHex(PC, 4) + "\n";
		statInfo += " [PC]=";
		statInfo += " " + formatHex(memoryRead(PC + 0), 2);
		statInfo += " " + formatHex(memoryRead(PC + 1), 2);
		statInfo += " " + formatHex(memoryRead(PC + 2), 2);
		statInfo += " " + formatHex(memoryRead(PC + 3), 2);
		statInfo += "\n";
		statInfo += " MHZ=" + formatDec((int) cpuSpeedCurrent, 3) + " [" + refreshDelayPerSecond + " ms/s]\n";
		if (threadError != null)
			statInfo += threadError + "\n";

		return statInfo;
	}

	/**
	 * Noise function
	 *
	 * We assume a "sort-of" floating bus
	 * (spanning memory locations 0000-3FFF)
	 *
	 * Correct way: We should look up the current video mode,
	 * and sample according to what is being shown.
	 */
	 public int noise() {
		 return mem[clock & 0x3fff];
	 }


	 
	/**
	 * Read memory function
	 *
	 * @param	address	Address
	 */
	protected int memoryRead(int address) {
		if ((address & 0xff00) == 0xc000)
			return ioRead(address);

		return (mem[address + memoryReadOffset[address >> 8]] & 0xff);
	}
	 
	/**
 	 * Write memory function
	 *
	 * @param	adderss	Address
	 * @param	value	value
	 */
	protected void memoryWrite(int address, int value) {
		if ((address & 0xff00) == 0xc000)
			ioWrite(address, value);
		else {
			mem[address + memoryWriteOffset[address >> 8]] = (byte) value;
			graphicsDirty[address >> 7] = true;
		}
	}

	/**
	 * Update memory maps
	 */
	private void updateMainMemoryMap() {
		int ramReadOffset, textReadOffset, hiresReadOffset;
		int ramWriteOffset, textWriteOffset, hiresWriteOffset;
		boolean isPage2 = ((graphicsMode & GR_PAGE2) != 0);
		boolean is80STORE = ((graphicsMode & GR_80STORE) != 0);
		boolean isHires = ((graphicsMode & GR_HIRES) != 0);

		textReadOffset = hiresReadOffset = ramReadOffset = isAuxRead ? 
			(MEM_AUX_RAM1 - MEM_PHYS_RAM1) : (MEM_MAIN_RAM1 - MEM_PHYS_RAM1);
		textWriteOffset = hiresWriteOffset = ramWriteOffset = isAuxWrite ?
			(MEM_AUX_RAM1 - MEM_PHYS_RAM1) : (MEM_MAIN_RAM1 - MEM_PHYS_RAM1);

		if (is80STORE) {
			textWriteOffset = textReadOffset = isPage2 ? (MEM_AUX_TEXT - MEM_PHYS_TEXT) : 
				(MEM_MAIN_TEXT - MEM_PHYS_TEXT);

			if (isHires)
				hiresWriteOffset = hiresReadOffset = textReadOffset;
		}
		
		memoryReadOffset[0x02] = memoryReadOffset[0x03] = ramReadOffset;
		memoryWriteOffset[0x02] = memoryWriteOffset[0x03] = ramWriteOffset;
		memoryReadOffset[0x04] = memoryReadOffset[0x05] = memoryReadOffset[0x06] = memoryReadOffset[0x07] = textReadOffset;
		memoryWriteOffset[0x04] = memoryWriteOffset[0x05] = memoryWriteOffset[0x06] = memoryWriteOffset[0x07] = textWriteOffset;
		for (int offset = 0x08; offset < 0x20; offset++) {
			memoryReadOffset[offset] = ramReadOffset;
			memoryWriteOffset[offset] = ramWriteOffset;
		}
		for (int offset = 0x20; offset < 0x40; offset++) {
			memoryReadOffset[offset] = hiresReadOffset;
			memoryWriteOffset[offset] = hiresWriteOffset;
		}
		for (int offset = 0x40; offset < 0xc0; offset++) {
			memoryReadOffset[offset] = ramReadOffset;
			memoryWriteOffset[offset] = ramWriteOffset;
		}
	}

	private void updateIOMemoryMap() {
		int romOffset;

		if (isRomInternal)
			romOffset = (MEM_ROM_INTERNAL - MEM_PHYS_IO);
		else
			romOffset = (MEM_ROM_EXTERNAL - MEM_PHYS_IO);
	
		for (int offset = 0xc1; offset < 0xd0; offset++)
			memoryReadOffset[offset] = romOffset;

		if (isRomC3External)
			memoryReadOffset[0xc3] = (MEM_ROM_EXTERNAL - MEM_PHYS_IO);
		else
			memoryReadOffset[0xc3] = (MEM_ROM_INTERNAL - MEM_PHYS_IO);
	}

	private void initIOMemoryMap() {
		for (int offset = 0xc1; offset < 0xd0; offset++)
			memoryWriteOffset[offset] = (MEM_WASTE - MEM_PHYS_IO);
	}

	private void updateLCMemoryMap() {
		int lcReadOffset, lcReadOffsetHigh;
		int lcWriteOffset, lcWriteOffsetHigh;
		
		if (!isLcReadEnable) {
			lcReadOffset = (MEM_ROM_MAIN_LOW - MEM_PHYS_ROM_LOW);
			lcReadOffsetHigh = (MEM_ROM_MAIN_LOW - MEM_PHYS_ROM_LOW);
		} else if (isAuxZeroPage) {
			lcReadOffset = isLcBank2 ? (MEM_AUX_LC2 - MEM_PHYS_ROM_LOW) : (MEM_AUX_LC1 - MEM_PHYS_ROM_LOW);
			lcReadOffsetHigh = (MEM_AUX_LC_HIGH - MEM_PHYS_ROM_HIGH);
		} else {
			lcReadOffset = isLcBank2 ? (MEM_MAIN_LC2 - MEM_PHYS_ROM_LOW) : (MEM_MAIN_LC1 - MEM_PHYS_ROM_LOW);
			lcReadOffsetHigh = (MEM_MAIN_LC_HIGH - MEM_PHYS_ROM_HIGH);
		}

		if (!isLcWriteEnable) {
			lcWriteOffset = (MEM_WASTE - MEM_PHYS_ROM_LOW);
			lcWriteOffsetHigh = (MEM_WASTE - MEM_PHYS_ROM_HIGH);
		} else if (isAuxZeroPage) {
			lcWriteOffset = isLcBank2 ? (MEM_AUX_LC2 - MEM_PHYS_ROM_LOW) : (MEM_AUX_LC1 - MEM_PHYS_ROM_LOW);
			lcWriteOffsetHigh = (MEM_AUX_LC_HIGH - MEM_PHYS_ROM_HIGH);
		} else {
			lcWriteOffset = isLcBank2 ? (MEM_MAIN_LC2 - MEM_PHYS_ROM_LOW) : (MEM_MAIN_LC1 - MEM_PHYS_ROM_LOW);
			lcWriteOffsetHigh = (MEM_MAIN_LC_HIGH - MEM_PHYS_ROM_HIGH);
		}
		
		for (int offset = 0xd0; offset < 0xe0; offset++) {
			memoryReadOffset[offset] = lcReadOffset;
			memoryWriteOffset[offset] = lcWriteOffset;
		}
		for (int offset = 0xe0; offset < 0x100; offset++) {
			memoryReadOffset[offset] = lcReadOffsetHigh;
			memoryWriteOffset[offset] = lcWriteOffsetHigh;
		}
	}

	void initMemoryMap() {
		initIOMemoryMap();

		updateMainMemoryMap();
		updateIOMemoryMap();
		updateLCMemoryMap();
	}
	
	/**
 	 * Apple I/O reads 
	 *
	 * @param	address	Address
	 */
	private int ioRead(int address) {
		address &= 0xff;

		if (address >= 0x90)
			return slots[(address & 0x70) >> 4].ioRead(address);

		switch (address) {
		case 0x00: case 0x01: case 0x02: case 0x03:
		case 0x04: case 0x05: case 0x06: case 0x07:
		case 0x08: case 0x09: case 0x0a: case 0x0b:
		case 0x0c: case 0x0d: case 0x0e: case 0x0f:
			// Keyboard data
			return keyboardLatch;

		case 0x10:
			// Keyboard strobe
			keyboardLatch &= 0x7f;
			return keyboardLatch;
		case 0x11:
			// Reading from LC Bank 2?
			return (keyboardLatch & 0x7f) | (isLcBank2 ? 0x80 : 0x00);
		case 0x12:
			// Reading from LC?
			return (keyboardLatch & 0x7f) | (isLcReadEnable ? 0x80 : 0x00);
		case 0x13:
			// Reading aux memory?
			return (keyboardLatch & 0x7f) | (isAuxRead ? 0x80 : 0x00);
		case 0x14:
			// Writing aux memory?
			return (keyboardLatch & 0x7f) | (isAuxWrite ? 0x80 : 0x00);
		case 0x15:
			// Using internal slot ROM?
			return (keyboardLatch & 0x7f) | (isRomInternal ? 0x80 : 0x00);
		case 0x16:
			// Using slot zero page+stack+LC?
			return (keyboardLatch & 0x7f) | (isAuxZeroPage ? 0x80 : 0x00);
		case 0x17:
			// Using external slot 3 ROM?
			return (keyboardLatch & 0x7f) | (isRomC3External ? 0x80 : 0x00);
		case 0x18:
			// 80STORE?
			return (keyboardLatch & 0x7f) | (((graphicsMode & GR_80STORE) != 0) ? 0x80 : 0x00);
		case 0x19:
			// VBL Signal low?
			isVideoVBL = !isVideoVBL;
			return (keyboardLatch & 0x7f) | (isVideoVBL ? 0x80 : 0x00);
		case 0x1a:
			// Using text mode?
			return (keyboardLatch & 0x7f) | (((graphicsMode & GR_TEXT) != 0) ? 0x80 : 0x00);
		case 0x1b:
			// Using mixed mode?
			return (keyboardLatch & 0x7f) | (((graphicsMode & GR_MIXMODE) != 0) ? 0x80 : 0x00);
		case 0x1c:
			// Using page 2?
			return (keyboardLatch & 0x7f) | (((graphicsMode & GR_PAGE2) != 0) ? 0x80 : 0x00);
		case 0x1d:
			// Using hires?
			return (keyboardLatch & 0x7f) | (((graphicsMode & GR_HIRES) != 0) ? 0x80 : 0x00);
		case 0x1e:
			// Using alt charset?
			return (keyboardLatch & 0x7f) | (((graphicsMode & GR_ALTCHAR) != 0) ? 0x80 : 0x00);
		case 0x1f:
			// Using 80-column display mode?
			return (keyboardLatch & 0x7f) | (((graphicsMode & GR_80CHAR) != 0) ? 0x80 : 0x00);

		case 0x20: case 0x21: case 0x22: case 0x23:
		case 0x24: case 0x25: case 0x26: case 0x27:
		case 0x28: case 0x29: case 0x2a: case 0x2b:
		case 0x2c: case 0x2d: case 0x2e: case 0x2f:
			// Cassette output
			break;

		case 0x30: case 0x31: case 0x32: case 0x33:
		case 0x34: case 0x35: case 0x36: case 0x37:
		case 0x38: case 0x39: case 0x3a: case 0x3b:
		case 0x3c: case 0x3d: case 0x3e: case 0x3f:
			// Speaker
			speakerFlips[speakerFlipsPointer] = clock;
			speakerFlipsPointer = (speakerFlipsPointer + 1) & SPEAKER_FLIPS_MASK;
			break;

		case 0x40: case 0x41: case 0x42: case 0x43:
		case 0x44: case 0x45: case 0x46: case 0x47:
		case 0x48: case 0x49: case 0x4a: case 0x4b:
		case 0x4c: case 0x4d: case 0x4e: case 0x4f:
			// Game strobe
			break;

		case 0x50:
			graphicsMode &= ~GR_TEXT;
			break;
		case 0x51:
			graphicsMode |= GR_TEXT;
			break;
		case 0x52:
			graphicsMode &= ~GR_MIXMODE;
			break;
		case 0x53:
			graphicsMode |= GR_MIXMODE;
			break;
		case 0x54:
			graphicsMode &= ~GR_PAGE2;
			updateMainMemoryMap();
			break;
		case 0x55:
			graphicsMode |= GR_PAGE2;
			updateMainMemoryMap();
			break;
		case 0x56:
			graphicsMode &= ~GR_HIRES;
			updateMainMemoryMap();
			break;
		case 0x57:
			graphicsMode |= GR_HIRES;
			updateMainMemoryMap();
			break;
		case 0x58: case 0x59: case 0x5a: case 0x5b:
		case 0x5c: case 0x5d:
			// Annunciators
			break;
		case 0x5e:
			graphicsMode |= GR_DHIRES;
			break;
		case 0x5f:
			graphicsMode &= ~GR_DHIRES;
			break;

		case 0x60:
		case 0x68:
			// (Also cassette input)
			return paddle.getButtonRegister(3);
		case 0x61: 
		case 0x69:
			return paddle.getButtonRegister(0);
		case 0x62:
		case 0x6a:
			return paddle.getButtonRegister(1);
		case 0x63:
		case 0x6b:
			return paddle.getButtonRegister(2);		
		case 0x64:
		case 0x6c:
			return paddle.getPaddleRegister(0);
		case 0x65:
		case 0x6d:
			return paddle.getPaddleRegister(1);
		case 0x66:
		case 0x6e:
			return paddle.getPaddleRegister(2);
		case 0x67:
		case 0x6f:
			return paddle.getPaddleRegister(3);

		case 0x70: case 0x71: case 0x72: case 0x73:
		case 0x74: case 0x75: case 0x76: case 0x77:
		case 0x78: case 0x79: case 0x7a: case 0x7b:
		case 0x7c: case 0x7d: case 0x7e: case 0x7f:
			paddle.triggerRegister();
			break;

		case 0x80:
		case 0x84:
			isLcBank2 = true;
			isLcReadEnable = true;
			isLcWriteEnable = false;
			updateLCMemoryMap();
			break;
		case 0x81:
		case 0x85:
			isLcBank2 = true;
			isLcReadEnable = false;
			isLcWriteEnable = true;
			updateLCMemoryMap();
			break;
		case 0x82:
		case 0x86:
			isLcBank2 = true;
			isLcReadEnable = false;
			isLcWriteEnable = false;
			updateLCMemoryMap();
			break;
		case 0x83:
		case 0x87:
			isLcBank2 = true;
			isLcReadEnable = true;
			isLcWriteEnable = true;
			updateLCMemoryMap();
			break;
		case 0x88:
		case 0x8c:
			isLcBank2 = false;
			isLcReadEnable = true;
			isLcWriteEnable = false;
			updateLCMemoryMap();
			break;
		case 0x89:
		case 0x8d:
			isLcBank2 = false;
			isLcReadEnable = false;
			isLcWriteEnable = true;
			updateLCMemoryMap();
			break;
		case 0x8a:
		case 0x8e:
			isLcBank2 = false;
			isLcReadEnable = false;
			isLcWriteEnable = false;
			updateLCMemoryMap();
			break;
		case 0x8b:
		case 0x8f:
			isLcBank2 = false;
			isLcReadEnable = true;
			isLcWriteEnable = true;
			updateLCMemoryMap();
			break;
		case 0x90: case 0x91: case 0x92: case 0x93:
		case 0x94: case 0x95: case 0x96: case 0x97:
		case 0x98: case 0x99: case 0x9a: case 0x9b:
		case 0x9c: case 0x9d: case 0x9e: case 0x9f:
			return slots[1].ioRead(address);
		
		case 0xa0: case 0xa1: case 0xa2: case 0xa3:
		case 0xa4: case 0xa5: case 0xa6: case 0xa7:
		case 0xa8: case 0xa9: case 0xaa: case 0xab:
		case 0xac: case 0xad: case 0xae: case 0xaf:
			return slots[2].ioRead(address);
		
		case 0xb0: case 0xb1: case 0xb2: case 0xb3:
		case 0xb4: case 0xb5: case 0xb6: case 0xb7:
		case 0xb8: case 0xb9: case 0xba: case 0xbb:
		case 0xbc: case 0xbd: case 0xbe: case 0xbf:
			return slots[3].ioRead(address);

		case 0xc0: case 0xc1: case 0xc2: case 0xc3:
		case 0xc4: case 0xc5: case 0xc6: case 0xc7:
		case 0xc8: case 0xc9: case 0xca: case 0xcb:
		case 0xcc: case 0xcd: case 0xce: case 0xcf:
			return slots[4].ioRead(address);

		case 0xd0: case 0xd1: case 0xd2: case 0xd3:
		case 0xd4: case 0xd5: case 0xd6: case 0xd7:
		case 0xd8: case 0xd9: case 0xda: case 0xdb:
		case 0xdc: case 0xdd: case 0xde: case 0xdf:
			return slots[5].ioRead(address);
		
		case 0xe0: case 0xe1: case 0xe2: case 0xe3:
		case 0xe4: case 0xe5: case 0xe6: case 0xe7:
		case 0xe8: case 0xe9: case 0xea: case 0xeb:
		case 0xec: case 0xed: case 0xee: case 0xef:
			return slots[6].ioRead(address);
		
		case 0xf0: case 0xf1: case 0xf2: case 0xf3:
		case 0xf4: case 0xf5: case 0xf6: case 0xf7:
		case 0xf8: case 0xf9: case 0xfa: case 0xfb:
		case 0xfc: case 0xfd: case 0xfe: case 0xff:
			return slots[7].ioRead(address);
		}

		return noise();
	}

	/**
 	 * Apple I/O writes
	 *
	 * @param	address	Address
	 * @param	value	Value
	 */
	private void ioWrite(int address, int value) {
		address &= 0xff;

		if (address >= 0x90) {
			slots[(address & 0x70) >> 4].ioWrite(address, value);
			return;
		}

		switch (address) {
		case 0x00:
			// 80STORE off
			graphicsMode &= ~GR_80STORE;
			updateMainMemoryMap();
			return;
		case 0x01:
			// 80STORE on
			graphicsMode |= GR_80STORE;
			updateMainMemoryMap();
			return;
		case 0x02:
			// Read aux mem off
			isAuxRead = false;
			updateMainMemoryMap();
			return;
		case 0x03:
			// Read aux mem on
			isAuxRead = true;
			updateMainMemoryMap();
			return;
		case 0x04:
			// Write aux mem off
			isAuxWrite = false;
			updateMainMemoryMap();
			return;
		case 0x05:
			// Write aux mem on
			isAuxWrite = true;
			updateMainMemoryMap();
			return;
		case 0x06:
			// Do not use internal ROM
			isRomInternal = false;
			updateIOMemoryMap();
			return;
		case 0x07:
			// Use internal ROM
			isRomInternal = true;
			updateIOMemoryMap();
			return;
		case 0x08:
			if (isAuxZeroPage) {
				// Physically get main zero page
				System.arraycopy(mem, MEM_PHYS_ZP, mem, MEM_AUX_ZP, 0x200);
				System.arraycopy(mem, MEM_MAIN_ZP, mem, MEM_PHYS_ZP, 0x200);
			}
			// Aux zero page off
			isAuxZeroPage = false;
			updateLCMemoryMap();
			return;
		case 0x09:
			if (!isAuxZeroPage) {
				// Physically get aux zero page
				System.arraycopy(mem, MEM_PHYS_ZP, mem, MEM_MAIN_ZP, 0x200);
				System.arraycopy(mem, MEM_AUX_ZP, mem, MEM_PHYS_ZP, 0x200);
			}
			// Aux zero page on
			isAuxZeroPage = true;
			updateLCMemoryMap();
			return;
		case 0x0a:
			// Do not use external slot 3 ROM
			isRomC3External = false;
			updateIOMemoryMap();
			return;
		case 0x0b:
			// Use external slot 3 ROM
			isRomC3External = true;
			updateIOMemoryMap();
			return;
		case 0x0c:
			// 80c off
			graphicsMode &= ~GR_80CHAR;
			return;
		case 0x0d:
			// 80c on
			graphicsMode |= GR_80CHAR;
			return;
		case 0x0e:
			// Alt charset off
			graphicsMode &= ~GR_ALTCHAR;
			return;
		case 0x0f:
			// Alt charset on
			graphicsMode |= GR_ALTCHAR;
			return;

		case 0x10: case 0x11: case 0x12: case 0x13:
		case 0x14: case 0x15: case 0x16: case 0x17:
		case 0x18: case 0x19: case 0x1a: case 0x1b:
		case 0x1c: case 0x1d: case 0x1e: case 0x1f:
			// Keyboard strobe
			keyboardLatch &= 0x7f;
			return;

		case 0x20: case 0x21: case 0x22: case 0x23:
		case 0x24: case 0x25: case 0x26: case 0x27:
		case 0x28: case 0x29: case 0x2a: case 0x2b:
		case 0x2c: case 0x2d: case 0x2e: case 0x2f:
			// Cassette output
			return;

		case 0x30: case 0x31: case 0x32: case 0x33:
		case 0x34: case 0x35: case 0x36: case 0x37:
		case 0x38: case 0x39: case 0x3a: case 0x3b:
		case 0x3c: case 0x3d: case 0x3e: case 0x3f:
			// Speaker
			speakerFlips[speakerFlipsPointer] = clock;
			speakerFlipsPointer = (speakerFlipsPointer + 1) & SPEAKER_FLIPS_MASK;
			return;

		case 0x40: case 0x41: case 0x42: case 0x43:
		case 0x44: case 0x45: case 0x46: case 0x47:
		case 0x48: case 0x49: case 0x4a: case 0x4b:
		case 0x4c: case 0x4d: case 0x4e: case 0x4f:
			// Game strobe
			return;

		case 0x50:
			graphicsMode &= ~GR_TEXT;
			return;
		case 0x51:
			graphicsMode |= GR_TEXT;
			return;
		case 0x52:
			graphicsMode &= ~GR_MIXMODE;
			return;
		case 0x53:
			graphicsMode |= GR_MIXMODE;
			return;
		case 0x54:
			graphicsMode &= ~GR_PAGE2;
			updateMainMemoryMap();
			return;
		case 0x55:
			graphicsMode |= GR_PAGE2;
			updateMainMemoryMap();
			return;
		case 0x56:
			graphicsMode &= ~GR_HIRES;
			updateMainMemoryMap();
			return;
		case 0x57:
			graphicsMode |= GR_HIRES;
			updateMainMemoryMap();
			return;
		case 0x58: case 0x59: case 0x5a: case 0x5b:
		case 0x5c: case 0x5d:
			// Annunciators
			return;
		case 0x5e:
			graphicsMode |= GR_DHIRES;
			break;
		case 0x5f:
			graphicsMode &= ~GR_DHIRES;
			break;

		case 0x60: case 0x61: case 0x62: case 0x63:
		case 0x64: case 0x65: case 0x66: case 0x67:
		case 0x68: case 0x69: case 0x6a: case 0x6b:
		case 0x6c: case 0x6d: case 0x6e: case 0x6f:
			// Cassette input/paddle data
			return;

		case 0x70: case 0x71: case 0x72: case 0x73:
		case 0x74: case 0x75: case 0x76: case 0x77:
		case 0x78: case 0x79: case 0x7a: case 0x7b:
		case 0x7c: case 0x7d: case 0x7e: case 0x7f:
			paddle.triggerRegister();
			return;

		case 0x80:
		case 0x84:
			isLcBank2 = true;
			isLcReadEnable = true;
			isLcWriteEnable = false;
			updateLCMemoryMap();
			break;
		case 0x81:
		case 0x85:
			isLcBank2 = true;
			isLcReadEnable = false;
			isLcWriteEnable = true;
			updateLCMemoryMap();
			break;
		case 0x82:
		case 0x86:
			isLcBank2 = true;
			isLcReadEnable = false;
			isLcWriteEnable = false;
			updateLCMemoryMap();
			break;
		case 0x83:
		case 0x87:
			isLcBank2 = true;
			isLcReadEnable = true;
			isLcWriteEnable = true;
			updateLCMemoryMap();
			break;
		case 0x88:
		case 0x8c:
			isLcBank2 = false;
			isLcReadEnable = true;
			isLcWriteEnable = false;
			updateLCMemoryMap();
			break;
		case 0x89:
		case 0x8d:
			isLcBank2 = false;
			isLcReadEnable = false;
			isLcWriteEnable = true;
			updateLCMemoryMap();
			break;
		case 0x8a:
		case 0x8e:
			isLcBank2 = false;
			isLcReadEnable = false;
			isLcWriteEnable = false;
			updateLCMemoryMap();
			break;
		case 0x8b:
		case 0x8f:
			isLcBank2 = false;
			isLcReadEnable = true;
			isLcWriteEnable = true;
			updateLCMemoryMap();
			break;
		}
	}

	/**
 	 * Emulator thread
 	 * 
 	 * TODO: The speaker has been merged into this thread.
 	 * This keeps it in sync but it still needs some work.
 	 * Speeding up the CPU (or adding fast disk access as below)
 	 * requires proper refactoring of the AppleSpeaker class. 
	 */
	public void run() {
		try {
			while (!isPaused) {
				long refreshStart = System.currentTimeMillis();
				long refreshDelay;
				
				checkInterrupts();

//				try {
				if (isStepMode) {
					if (isNextStep) {
						isNextStep = false;
						executeInstructions(stepCount);
					}
				} else {
					int clocksNeeded = clocksPerInterval;
					while (clocksNeeded > 0)
						clocksNeeded -= executeInstructions(1 + (clocksNeeded >> 3));
				}
//				}
//				catch (RuntimeException e)
//				{
//					setStepMode(true); // TODO: for breakpoint hack - disable
//				}

				// TODO: need something like the following for fast disk access
				//if (slots[6] instanceof DiskII && !((DiskII)slots[6]).isMotorOn())

				speaker.refreshSpeaker(); // NOTE: this blocks, syncing emulation and sound
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
	 * Reset assertion code
	 */
	protected void onReset() {
		// Reset IOU + MMU
		ioWrite(0x00, 0);
		ioWrite(0x02, 0);
		ioWrite(0x04, 0);
		ioWrite(0x06, 0);
		ioWrite(0x08, 0);
		ioWrite(0x0a, 0);
		ioWrite(0x0c, 0);
		ioWrite(0x0e, 0);
		ioWrite(0x50, 0);
		ioWrite(0x52, 0);
		ioWrite(0x54, 0);
		ioWrite(0x56, 0);
		ioWrite(0x5F, 0);
		ioWrite(0x82, 0);

		if (isRestart) {
			// Clear RAM
			for (int i = 0; i < MEM_ROM_MAIN_LOW; i++)
				mem[i] = 0;

			setRandomSeed();
		}

		// Reset devices
		for (int slot = 1; slot < 8; slot++)
			slots[slot].reset();
	}
}
