package com.hlag.oversigt.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Test;

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

}
