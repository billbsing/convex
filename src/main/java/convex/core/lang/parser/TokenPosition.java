package convex.core.lang.parser;

import java.lang.StringBuffer;

public class TokenPosition {
	protected int index;
	protected int column;
	protected int row;

	private TokenPosition(int index, int column, int row) {
		this.index = index;
		this.column = column;
		this.row = row;
	}

	public static TokenPosition create() {
		return new TokenPosition(0, 0, 0);
	}

	public static TokenPosition create(TokenPosition position) {
		return new TokenPosition(position.getIndex(), position.getColumn(), position.getRow());
	}

	public int getIndex() {
		return index;
	}

	public void move(int columnOffset, int rowOffset) {
		column += columnOffset;
		row += rowOffset;
	}

	public void next() {
		next(1);
	}

	public void next(int offset) {
		index += offset;
	}

	public int getColumn() {
		return column;
	}

	public void setColumn(int value) {
		column = value;
	}

	public int getRow() {
		return row;
	}
}
