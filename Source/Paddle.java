
/**
* AppleIIGo
 * Apple II Emulator for J2ME
 * (C) 2006 by Marc S. Ressl(ressl@lonetree.com)
 * Released under the GPL
 */

public class Paddle {
	// Public variables
	public static final int PADDLE_LOW = 0;
	public static final int PADDLE_CENTER = 127;
	public static final int PADDLE_HIGH = 255;

	public static final int PADDLEMODE_DIRECT = 0;
	public static final int PADDLEMODE_FILTERED = 1;
	
	// Instances of other classes
	private EmAppleII apple;

	// Button variables
	private int[] buttonRegister = new int[4];

	// Paddle variables
	// private int paddleMode; // TODO: Was this for analog/digital mode? (Nick)

	private int[] paddleClockEvent = new int[4];
	private int[] paddleClockInc = new int[4];

	/**
	 * Paddle class constructor
	 *
	 * @param	apple	The EmAppleII instance
	 */
	public Paddle(EmAppleII apple) {
		this.apple = apple;

		setPaddlePos(0, PADDLE_HIGH);
		setPaddlePos(1, PADDLE_HIGH);
		setPaddlePos(2, PADDLE_HIGH);
		setPaddlePos(3, PADDLE_HIGH);
	}
	
	/**
	 * Set button state
	 *
	 * @param	button	Paddle button
	 * @param	state	State
	 */
	public void setButton(int button, boolean pressed) {
		buttonRegister[button] = (pressed ? 0x80 : 0x00);
	}
	
	/**
	 * Button register
	 *
	 * @param	button	Paddle button
	 */
	public int getButtonRegister(int button) {
		return buttonRegister[button];
	}

	/**
	 * Set paddle position
	 *
	 * @param	address	Address
	 * @param	value	Value
	 */
	public void setPaddlePos(int paddle, int value) {
		/*
		 * Magic formula, see ROM $FB1E-$FB2E,
		 * We calculate the numbers of cycles after which
		 * the RC circuit of a triggered paddle will discharge.
		 */
		paddleClockInc[paddle] = value * 11 + 8;
	}
	
	/**
	 * Trigger paddle register
	 *
	 * @param	address	Address
	 * @param	value	Value
	 */
	public void triggerRegister() {
		paddleClockEvent[0] = apple.clock + paddleClockInc[0];
		paddleClockEvent[1] = apple.clock + paddleClockInc[1];
		paddleClockEvent[2] = apple.clock + paddleClockInc[2];
		paddleClockEvent[3] = apple.clock + paddleClockInc[3];
	}
	
	/**
	 * Get paddle register
	 *
	 * @param	address	Address
	 * @param	value	Value
	 */
	public int getPaddleRegister(int paddle) {
		return ((((paddleClockEvent[paddle] - apple.clock) & 0x7fffffff) < 0x40000000) ? 0x80 : 0x00); 
	}
}
