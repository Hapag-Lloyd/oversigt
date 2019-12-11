package com.hlag.oversigt.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hlag.oversigt.storage.SqlDialect.ColumnOptions;
import com.hlag.oversigt.storage.SqlDialect.ColumnType;

public class SqliteDialectTest {
	private SqlDialect dialect = new SqliteDialect();

	@Test
	public void generateSelect_selectAsteriscWhere() {
		final String expected = "SELECT * FROM TABLE WHERE A = ? AND B = ? AND C = ?";
		final String actual = dialect.select("TABLE", Arrays.asList("A", "B", "C"));

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateSelect_selectAbcWhere() {
		final String expected = "SELECT A,B,C FROM TABLE WHERE D = ? AND E = ? AND F = ?";
		final String actual = dialect.select("TABLE", Arrays.asList("A", "B", "C"), Arrays.asList("D", "E", "F"));

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateSelect_selectAbcWhereIn() {
		final String expected = "SELECT A,B,C FROM TABLE WHERE D IN (?, ?, ?, ?, ?)";
		final String actual = dialect.select("TABLE", Arrays.asList("A", "B", "C"), Optional.of("D"), 5);

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateSelect_selectAbcWhereWithoutIn() {
		final String expected = "SELECT A, B, C FROM TABLE";
		final String actual = dialect.select("TABLE", Arrays.asList("A", "B", "C"), Optional.empty(), 5);

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateSelect_selectAbcWhereAndIn() {
		final String expected = "SELECT A,B,C FROM TABLE WHERE D=? AND E=? AND F=? AND NOT G IN ( ?, ?, ?)";
		final String actual = dialect
				.select("TABLE", Arrays.asList("A", "B", "C"), Arrays.asList("D", "E", "F"), Optional.of("G"), true, 3);

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateSelect_selectAbcWhereAndLike() {
		final String expected = "SELECT A,B,C FROM TABLE WHERE D=? AND E=? AND F=? AND G LIKE  ?";
		final String actual
				= dialect.selectWithOneLike("TABLE", Arrays.asList("A", "B", "C"), Arrays.asList("D", "E", "F"), "G");

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateDelete_delete() {
		final String expected = "DELETE FROM TABLE WHERE A=? AND B=? AND C=?";
		final String actual = dialect.delete("TABLE", Arrays.asList("A", "B", "C"));

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateDelete_deleteWithIn() {
		final String expected = "DELETE FROM TABLE WHERE A=? AND B=? AND C=? AND NOT D IN (?,?,?,?)";
		final String actual = dialect.delete("TABLE", Arrays.asList("A", "B", "C"), Optional.of("D"), true, 4);

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateUpdate() {
		final String expected = "UPDATE TABLE SET D=?, E=?, F=? WHERE A=? AND B=? AND C=?";
		final String actual = dialect.update("TABLE", Arrays.asList("D", "E", "F"), Arrays.asList("A", "B", "C"));

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateInsert() {
		final String expected = "INSERT INTO TABLE (A,B,C) VALUES(?,?,?)";
		final String actual = dialect.insert("TABLE", Arrays.asList("A", "B", "C"));

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateCreate_twoColumns() {
		final ColumnOptions option1 = new ColumnOptions("MNO", ColumnType.Text, "abc", false, false);
		final ColumnOptions option2 = new ColumnOptions("GHI", ColumnType.Integer, "4", false, true);
		final String expected = "CREATE TABLE XYZ (MNO TEXT NOT NULL DEFAULT 'abc', GHI INTEGER PRIMARY KEY NOT NULL)";
		final String actual = dialect.createTable("XYZ", option1, option2);

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateCreate_empty() {
		final String expected = "CREATE TABLE XYZ ()";
		final String actual = dialect.createTable("XYZ");

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateAlter_addColumn() {
		final ColumnOptions options = new ColumnOptions("MNO", ColumnType.Text, "abc", false, false);
		final String expected = "ALTER TABLE XYZ ADD COLUMN MNO TEXT NOT NULL DEFAULT 'abc'";
		final String actual = dialect.alterTableAddColumn("XYZ", options);

		assertThat(actual).isEqualToIgnoringWhitespace(expected);
	}

	@Test
	public void generateAlter_dropColumn() {
		final UnsupportedOperationException actual = assertThrows(UnsupportedOperationException.class, () -> {
			dialect.alterTableDropColumn("XYZ", "MNO");
		});

		assertThat(actual).hasMessageContaining("not supported by SQLite");
	}

	@Test
	public void convertValue_boolean() {
		final Object expected = 1;
		final Object actual = dialect.convertValue(true);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void convertValue_Duration() {
		final Duration given = Duration.ofMinutes(3).plusSeconds(3);

		final Object expected = 183L;
		final Object actual = dialect.convertValue(given);

		assertThat(actual).isEqualTo(expected);
	}
}
