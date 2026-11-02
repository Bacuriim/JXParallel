package com.jxparallel.forge;

/** A JXML file that cannot be built, with the file and line where the problem is. */
public final class JxmlException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String source;
    private final int line;

    public JxmlException(String source, int line, String message) {
        this(source, line, message, null);
    }

    public JxmlException(String source, int line, String message, Throwable cause) {
        super(source + ":" + line + ": " + message, cause);
        this.source = source;
        this.line = line;
    }

    public String getSource() {
        return source;
    }

    public int getLine() {
        return line;
    }
}
