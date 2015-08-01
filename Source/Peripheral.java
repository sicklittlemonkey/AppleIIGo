
/**
 * AppleIIGo
 * Slot interface
 * (C) 2006 by Marc S. Ressl(mressl@gmail.com)
 * Released under the GPL
 * Based on work by Steven E. Hugg
 */

import java.util.Random;

public class Peripheral {
	protected Random rand = new Random();

    public Peripheral() {
	}
	
    public int ioRead(int address) {
		return rand.nextInt(256);
    }
	
    public void ioWrite(int address, int value) {
    }
	
    public int memoryRead(int address) {
		return 0;
    }
	
    public void memoryWrite(int address, int value) {
    }
	
	public void reset() {
	}
}
