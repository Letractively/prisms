package prisms.lang;

import prisms.arch.PrismsConfig;

public interface PrismsParserDebugger {
	void init(PrismsParser parser);

	void start(CharSequence text);

	void end(CharSequence text, ParseMatch [] matches);

	void fail(CharSequence text, ParseMatch [] matches, ParseException e);

	void preParse(CharSequence text, int index, PrismsConfig op);

	void postParse(CharSequence text, int startIndex, PrismsConfig op, ParseMatch match);
}
