
/**
 * AppleIIGo
 * Disk II Emulator
 * (C) 2006 by Marc S. Ressl(ressl@lonetree.com)
 * (C) 2009 by Nick Westgate (Nick.Westgate@gmail.com)
 * Released under the GPL
 * Based on work by Doug Kwan
 */
 
import java.io.*;

public class DiskII extends Peripheral {
	// ROM (with boot wait cycle optimization)
	private static final int[] rom = {
		0xA2,0x20,0xA0,0x00,0xA2,0x03,0x86,0x3C,0x8A,0x0A,0x24,0x3C,0xF0,0x10,0x05,0x3C,
		0x49,0xFF,0x29,0x7E,0xB0,0x08,0x4A,0xD0,0xFB,0x98,0x9D,0x56,0x03,0xC8,0xE8,0x10,
		0xE5,0x20,0x58,0xFF,0xBA,0xBD,0x00,0x01,0x0A,0x0A,0x0A,0x0A,0x85,0x2B,0xAA,0xBD,
		0x8E,0xC0,0xBD,0x8C,0xC0,0xBD,0x8A,0xC0,0xBD,0x89,0xC0,0xA0,0x50,0xBD,0x80,0xC0,
		0x98,0x29,0x03,0x0A,0x05,0x2B,0xAA,0xBD,0x81,0xC0,0xA9,0x56,0xa9,0x00,0xea,0x88,
		0x10,0xEB,0x85,0x26,0x85,0x3D,0x85,0x41,0xA9,0x08,0x85,0x27,0x18,0x08,0xBD,0x8C,
		0xC0,0x10,0xFB,0x49,0xD5,0xD0,0xF7,0xBD,0x8C,0xC0,0x10,0xFB,0xC9,0xAA,0xD0,0xF3,
		0xEA,0xBD,0x8C,0xC0,0x10,0xFB,0xC9,0x96,0xF0,0x09,0x28,0x90,0xDF,0x49,0xAD,0xF0,
		0x25,0xD0,0xD9,0xA0,0x03,0x85,0x40,0xBD,0x8C,0xC0,0x10,0xFB,0x2A,0x85,0x3C,0xBD,
		0x8C,0xC0,0x10,0xFB,0x25,0x3C,0x88,0xD0,0xEC,0x28,0xC5,0x3D,0xD0,0xBE,0xA5,0x40,
		0xC5,0x41,0xD0,0xB8,0xB0,0xB7,0xA0,0x56,0x84,0x3C,0xBC,0x8C,0xC0,0x10,0xFB,0x59,
		0xD6,0x02,0xA4,0x3C,0x88,0x99,0x00,0x03,0xD0,0xEE,0x84,0x3C,0xBC,0x8C,0xC0,0x10,
		0xFB,0x59,0xD6,0x02,0xA4,0x3C,0x91,0x26,0xC8,0xD0,0xEF,0xBC,0x8C,0xC0,0x10,0xFB,
		0x59,0xD6,0x02,0xD0,0x87,0xA0,0x00,0xA2,0x56,0xCA,0x30,0xFB,0xB1,0x26,0x5E,0x00,
		0x03,0x2A,0x5E,0x00,0x03,0x2A,0x91,0x26,0xC8,0xD0,0xEE,0xE6,0x27,0xE6,0x3D,0xA5,
		0x3D,0xCD,0x00,0x08,0xA6,0x2B,0x90,0xDB,0x4C,0x01,0x08,0x00,0x00,0x00,0x00,0x00,
	};	

	// Constants
	private static final int NUM_DRIVES = 2;
	private static final int DOS_NUM_SECTORS = 16;
	private static final int DOS_NUM_TRACKS = 35;
	private static final int DOS_TRACK_BYTES = 256 * DOS_NUM_SECTORS;
	private static final int RAW_TRACK_BYTES = 0x1A00; // 0x1A00 (6656) for .NIB (was 6250)
	public static final int DEFAULT_VOLUME = 254;
	
	// Disk II direct access variables
	private int drive = 0;
	private boolean isMotorOn = false;

	private byte[][][] diskData = new byte[NUM_DRIVES][DOS_NUM_TRACKS][];
	private boolean[] isWriteProtected = new boolean[NUM_DRIVES];

	private int currPhysTrack;
	private int currNibble;

	// Caches
	private int[] driveCurrPhysTrack = new int[NUM_DRIVES];
	private byte[] realTrack;
	
	/*
	 * Disk II emulation:
	 *
	 * C0xD, C0xE -> Read write protect
	 * C0xE, C0xC -> Read data from disk
	 * Write data to disk -> C0xF, C0xC
	 * Write data to disk -> C0xD, C0xC
	 *
	 * We use 'fast mode', i.e. no 65(C)02 clock reference
	 * We use simplified track handling (only adjacent phases)
	 */

	// Internal registers
	private int latchAddress;
	private int latchData;
	private boolean writeMode;
	
	// GCR encoding and decoding tables
	private static final int[] gcrEncodingTable = {
		0x96, 0x97, 0x9A, 0x9B, 0x9D, 0x9E, 0x9F, 0xA6,
		0xA7, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF, 0xB2, 0xB3,
		0xB4, 0xB5, 0xB6, 0xB7, 0xB9, 0xBA, 0xBB, 0xBC,
		0xBD, 0xBE, 0xBF, 0xCB, 0xCD, 0xCE, 0xCF, 0xD3,
		0xD6, 0xD7, 0xD9, 0xDA, 0xDB, 0xDC, 0xDD, 0xDE,
		0xDF, 0xE5, 0xE6, 0xE7, 0xE9, 0xEA, 0xEB, 0xEC,
		0xED, 0xEE, 0xEF, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6,
		0xF7, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF,
	};
	//private int[] gcrDecodingTable = new int[256];
	private int[] gcrSwapBit = {0, 2, 1, 3};
	private int[] gcrBuffer = new int[256];
	private int[] gcrBuffer2 = new int[86];
	
	// Physical sector to DOS 3.3 logical sector table
	private static final int[] gcrLogicalDos33Sector = {
		0x0, 0x7, 0xE, 0x6, 0xD, 0x5, 0xC, 0x4,
		0xB, 0x3, 0xA, 0x2, 0x9, 0x1, 0x8, 0xF };
	
	// Physical sector to DOS 3.3 logical sector table
	private static final int[] gcrLogicalProdosSector = {
		0x0, 0x8, 0x1, 0x9, 0x2, 0xA, 0x3, 0xB,
		0x4, 0xC, 0x5, 0xD, 0x6, 0xE, 0x7, 0xF };
	
	// Temporary variables for conversion
	private byte[] gcrNibbles = new byte[RAW_TRACK_BYTES];
	private int gcrNibblesPos;
	
	EmAppleII apple;

	/**
	 * Constructor
	 */
	public DiskII(EmAppleII apple) {
		super();
		this.apple = apple;
		
		readDisk(0, null, "", false, DEFAULT_VOLUME);
		readDisk(1, null, "", false, DEFAULT_VOLUME);
	}
	
	/**
	 * I/O read
	 *
	 * @param	address	Address
	 */
	public int ioRead(int address) {
		int phase;
		
		switch (address & 0xf) {
			case 0x0:
			case 0x2:			
			case 0x4:
			case 0x6:
				// Q0, Q1, Q2, Q3 off
				break;
			case 0x1:
				// Q0 on
				phase = currPhysTrack & 3;
				if (phase == 1) {
					if (currPhysTrack > 0)
						currPhysTrack--;
				} else if (phase == 3) {
					if (currPhysTrack < ((2 * DOS_NUM_TRACKS) - 1))
						currPhysTrack++;
				}
				//System.out.println("half track=" + currPhysTrack);
				realTrack = diskData[drive][currPhysTrack >> 1];
				break;
			case 0x3:
				// Q1 on
				phase = currPhysTrack & 3;
				if (phase == 2) {
					if (currPhysTrack > 0)
						currPhysTrack--;
				} else if (phase == 0) {
					if (currPhysTrack < ((2 * DOS_NUM_TRACKS) - 1))
						currPhysTrack++;
				}
				//System.out.println("half track=" + currPhysTrack);
				realTrack = diskData[drive][currPhysTrack >> 1];
				break;
			case 0x5:
				// Q2 on
				phase = currPhysTrack & 3;
				if (phase == 3) {
					if (currPhysTrack > 0)
						currPhysTrack--;
				} else if (phase == 1) {
					if (currPhysTrack < ((2 * DOS_NUM_TRACKS) - 1))
						currPhysTrack++;
				}
				//System.out.println("half track=" + currPhysTrack);
				realTrack = diskData[drive][currPhysTrack >> 1];
				break;
			case 0x7:
				// Q3 on
				phase = currPhysTrack & 3;
				if (phase == 0) {
					if (currPhysTrack > 0)
						currPhysTrack--;
				} else if (phase == 2) {
					if (currPhysTrack < ((2 * DOS_NUM_TRACKS) - 1))
						currPhysTrack++;
				}
				//System.out.println("half track=" + currPhysTrack);
				realTrack = diskData[drive][currPhysTrack >> 1];
				break;
			case 0x8:
				// Motor off
				isMotorOn = false;
				break;
			case 0x9:
				// Motor on
				isMotorOn = true;
				break;
			case 0xa:
				// Drive 1
				driveCurrPhysTrack[drive] = currPhysTrack;
				drive = 0;
				currPhysTrack = driveCurrPhysTrack[drive];

				realTrack = diskData[drive][currPhysTrack >> 1];
				break;
			case 0xb:
				// Drive 2
				driveCurrPhysTrack[drive] = currPhysTrack;
				drive = 1;
				currPhysTrack = driveCurrPhysTrack[drive];

				realTrack = diskData[drive][currPhysTrack >> 1];
				break;
			case 0xc:
				return ioLatchC();
			case 0xd:
				ioLatchD(0xff);
				break;
			case 0xe:
				return ioLatchE();
			case 0xf:
				ioLatchF(0xff);
				break;
		}
		
		return rand.nextInt(256);
    }
	
	/**
	 * I/O write
	 *
	 * @param	address	Address
	 */
	public void ioWrite(int address, int value) {
		switch (address & 0xf) {
			case 0x0:
			case 0x1:
			case 0x2:			
			case 0x3:
			case 0x4:
			case 0x5:
			case 0x6:
			case 0x7:
			case 0x8:
			case 0x9:
			case 0xa:
			case 0xb:
				ioRead(address);
				break;
			case 0xc:
				ioLatchC();
				break;
			case 0xd:
				ioLatchD(value);
				break;
			case 0xe:
				ioLatchE();
				break;
			case 0xf:
				ioLatchF(value);
				break;
		}
    }
	
	/**
	 * Memory read
	 *
	 * @param	address	Address
	 */
    public int memoryRead(int address) {
		return rom[address & 0xff];
    }

	/**
	 * Reset peripheral
	 */
	public void reset() {
		ioRead(0x8);
	}

	/**
 	 * Loads a disk
	 *
	 * @param	is			InputStream
	 * @param	drive		Disk II drive
	 */
	public boolean readDisk(int drive, DataInputStream is, String name, boolean isWriteProtected, int volumeNumber) {
		byte[] track = new byte[DOS_TRACK_BYTES];

		String lowerName = name.toLowerCase();
		boolean proDos = lowerName.indexOf(".po") != -1;
		boolean nib = lowerName.indexOf(".nib") != -1;
		
		try {
			for (int trackNum = 0; trackNum < DOS_NUM_TRACKS; trackNum++) {
				diskData[drive][trackNum] = new byte[RAW_TRACK_BYTES];

				if (is != null) {
					if (nib)
					{
						is.readFully(diskData[drive][trackNum], 0, RAW_TRACK_BYTES);
					}
					else
					{
						is.readFully(track, 0, DOS_TRACK_BYTES);
						trackToNibbles(track, diskData[drive][trackNum], volumeNumber, trackNum, !proDos);
					}
				}
			}

			this.realTrack = diskData[drive][currPhysTrack >> 1];
			this.isWriteProtected[drive] = isWriteProtected;
			
			return true;
		} catch (IOException e) {
		}
	
		return false;
	}

	/**
 	 * Writes a disk
	 *
	 * @param	is			InputStream
	 * @param	drive		Disk II drive
	 */
	public boolean writeDisk(int drive, OutputStream os) {
		return true;
	}

	/**
 	 * Motor on indicator
	 */
	public boolean isMotorOn() {
		return isMotorOn;
	}



	/**
	 * I/O read Latch C
	 *
	 * @param	address	Address
	 */
	private int ioLatchC() {
		latchAddress = 0xc;
		if (writeMode)
		{
			// Write data: C0xD, C0xC
			realTrack[currNibble] = (byte) latchData;
		}
		else
		{
			// Read data: C0xE, C0xC
			latchData = (realTrack[currNibble] & 0xff);

			// simple hack to help DOS find address prologues ($B94F)
			if (/* fastDisk && */ latchData != 0xD5 && // TODO: fastDisk property to enable/disable 
				apple.memoryRead(apple.PC + 3) == 0xD5 && // #$D5
				apple.memoryRead(apple.PC + 2) == 0xC9 && // CMP
				apple.memoryRead(apple.PC + 1) == 0xFB && // PC - 3
				apple.memoryRead(apple.PC + 0) == 0x10)   // BPL
			{
				int count = RAW_TRACK_BYTES / 16;
				do
				{
					currNibble++;
					if (currNibble >= RAW_TRACK_BYTES)
						currNibble = 0;
					latchData = (realTrack[currNibble] & 0xff);
				}
				while (latchData != 0xD5 && --count > 0);
			}
			// simple hack to fool DOS 3.3 RWTS drive spin detect routine ($BD34)
			else if (apple.memoryRead(apple.PC - 3) == 0xDD && // CMP $C08C,X
				apple.memoryRead(apple.PC + 1) == 0x03 &&      // PC + 3 
				apple.memoryRead(apple.PC + 0) == 0xD0)        // BNE
			{
				return 0x7F; 
			}
			// skip invalid nibbles we padded the track buffer with 
			else if (latchData == 0x7F)
			{
				int count = RAW_TRACK_BYTES / 16;
				do
				{
					currNibble++;
					if (currNibble >= RAW_TRACK_BYTES)
						currNibble = 0;
					latchData = (realTrack[currNibble] & 0xff);
				}
				while (latchData == 0x7F && --count > 0);
			}
		}

		currNibble++;
		if (currNibble >= RAW_TRACK_BYTES)
			currNibble = 0;

		return latchData;
	}
	
	/**
	 * I/O write Latch D
	 *
	 * @param	address	Address
	 */
	private void ioLatchD(int value) {
		// Prepare for write protect sense
		latchAddress = 0xd;
	}
	
	/**
	 * I/O read Latch E
	 *
	 * @param	address	Address
	 */
	private int ioLatchE() {
		// Read write-protect: C0xD, C0xE
		if (latchAddress == 0xd) {
			latchAddress = 0xe;
			return isWriteProtected[drive] ? 0x80 : 0x00;
		}

		writeMode = false;
		latchAddress = 0xe;
		return 0x3c;
	}

	/**
	 * I/O write Latch F
	 *
	 * @param	address	Address
	 */
	private void ioLatchF(int value) {
		// Prepare write
		writeMode = true;
		latchData = value;
		latchAddress = 0xf;
	}

	/**
	 * TRACK CONVERSION ROUTINES
	 */

	/**
 	 * Writes a nibble
	 *
	 * @param	value		Value
	 */
	private final void gcrWriteNibble(int value) {
		gcrNibbles[gcrNibblesPos] = (byte) value;
		gcrNibblesPos++;
	}

	/**
 	 * Writes nibbles
	 *
	 * @param	length		Number of bits
	 */
	private final void writeNibbles(int nibble, int length) {
		while(length > 0) {
			length--;
			gcrWriteNibble(nibble);
		}
	}

	/**
 	 * Writes sync nibbles
	 *
	 * @param	length		Number of bits
	 */
	private final void writeSync(int length) {
		writeNibbles(0xff, length);
	}

	/**
	 * Write an FM encoded value, used in writing address fields 
	 *
	 * @param	value		Value
	 */
	private final void encode44(int value) {
		gcrWriteNibble((value >> 1) | 0xaa);
		gcrWriteNibble(value | 0xaa);
	}

	/**
 	 * Encode in 6:2
	 *
	 * @param	track		Sectorized track data
	 * @param	offset		Offset in this data
	 */
	private void encode62(byte[] track, int offset) {
		// 86 * 3 = 258, so the first two byte are encoded twice
		gcrBuffer2[0] = gcrSwapBit[track[offset + 1] & 0x03];
		gcrBuffer2[1] = gcrSwapBit[track[offset] & 0x03];

		// Save higher 6 bits in gcrBuffer and lower 2 bits in gcrBuffer2
		for(int i = 255, j = 2; i >= 0; i--, j = j == 85 ? 0: j + 1) {
		   gcrBuffer2[j] = ((gcrBuffer2[j] << 2) | gcrSwapBit[track[offset + i] & 0x03]);
		   gcrBuffer[i] = (track[offset + i] & 0xff)  >> 2;
		}
		 
		// Clear off higher 2 bits of GCR_buffer2 set in the last call
		for(int i = 0; i < 86; i++)
		   gcrBuffer2[i] &= 0x3f;
	}

	/**
 	 * Write address field
	 *
	 * @param	track		Sectorized track data
	 * @param	offset		Offset in this data
	 */
	private final void writeAddressField(int volumeNum, int trackNum, int sectorNum) {
		// Write address mark
		gcrWriteNibble(0xd5);
		gcrWriteNibble(0xaa);
		gcrWriteNibble(0x96);
		 
		// Write volume, trackNum, sector & checksum
		encode44(volumeNum);
		encode44(trackNum);
		encode44(sectorNum);
		encode44(volumeNum ^ trackNum ^ sectorNum);
		 
		// Write epilogue
		gcrWriteNibble(0xde);
		gcrWriteNibble(0xaa);
		gcrWriteNibble(0xeb);
	}
	
	/**
 	 * Write data field
	 */
	private void writeDataField() {
		int last = 0;
		int checksum;

		// Write prologue
		gcrWriteNibble(0xd5);
		gcrWriteNibble(0xaa);
		gcrWriteNibble(0xad);

		// Write GCR encoded data
		for(int i = 0x55; i >= 0; i--) {
			checksum = last ^ gcrBuffer2[i];
			gcrWriteNibble(gcrEncodingTable[checksum]);
			last = gcrBuffer2[i];
		}
		for(int i = 0; i < 256; i++) {
			checksum = last ^ gcrBuffer[i];
			gcrWriteNibble(gcrEncodingTable[checksum]);
			last = gcrBuffer[i];
		}

		// Write checksum
		gcrWriteNibble(gcrEncodingTable[last]);

		// Write epilogue
		gcrWriteNibble(0xde);
		gcrWriteNibble(0xaa);
		gcrWriteNibble(0xeb);
	}

	/**
 	 * Converts a track to nibbles
	 */
	private void trackToNibbles(byte[] track, byte[] nibbles, int volumeNum, int trackNum, boolean dos) {
		this.gcrNibbles = nibbles;
		gcrNibblesPos = 0;
		int logicalSector[] = (dos) ? gcrLogicalDos33Sector : gcrLogicalProdosSector;

		for (int sectorNum = 0; sectorNum < DOS_NUM_SECTORS; sectorNum++) {
			encode62(track, logicalSector[sectorNum] << 8);
			writeSync(12);
			writeAddressField(volumeNum, trackNum, sectorNum);
			writeSync(8);
			writeDataField();
		}
		writeNibbles(0x7F, RAW_TRACK_BYTES - gcrNibblesPos); // invalid nibbles to skip on read
	}
}
