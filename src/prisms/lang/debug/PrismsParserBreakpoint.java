package prisms.lang.debug;

import java.util.regex.Pattern;

public class PrismsParserBreakpoint {
	private Pattern thePreCursorText;
	private Pattern thePostCursorText;
	private String theOpName;

	public PrismsParserBreakpoint() {
	}

	public Pattern getPreCursorText() {
		return thePreCursorText;
	}

	public void setPreCursorText(Pattern text) {
		thePreCursorText = text;
	}

	public Pattern getPostCursorText() {
		return thePostCursorText;
	}

	public void setPostCursorText(Pattern text) {
		thePostCursorText = text;
	}

	public String getOpName() {
		return theOpName;
	}

	public void setOpName(String opName) {
		theOpName = opName;
	}
}
