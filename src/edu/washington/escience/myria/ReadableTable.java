package edu.washington.escience.myria;

import org.joda.time.DateTime;

/**
 * An interface for objects that contain a table (2-D) of tuples that is readable.
 */
public interface ReadableTable extends TupleTable {
  /**
   * @param column the column of the desired value.
   * @param row the row of the desired value.
   * @return the value in the specified column and row.
   */
  boolean getBoolean(final int column, final int row);

  /**
   * @param column the column of the desired value.
   * @param row the row of the desired value.
   * @return the value in the specified column and row.
   */
  DateTime getDateTime(final int column, final int row);

  /**
   * @param column the column of the desired value.
   * @param row the row of the desired value.
   * @return the value in the specified column and row.
   */
  double getDouble(final int column, final int row);

  /**
   * @param column the column of the desired value.
   * @param row the row of the desired value.
   * @return the value in the specified column and row.
   */
  float getFloat(final int column, final int row);

  /**
   * @param column the column of the desired value.
   * @param row the row of the desired value.
   * @return the value in the specified column and row.
   */
  int getInt(final int column, final int row);

  /**
   * @param column the column of the desired value.
   * @param row the row of the desired value.
   * @return the value in the specified column and row.
   */
  long getLong(final int column, final int row);

  /**
   * @param column the column of the desired value.
   * @param row the row of the desired value.
   * @return the value in the specified column and row.
   */
  Object getObject(final int column, final int row);

  /**
   * @param column the column of the desired value.
   * @param row the row of the desired value.
   * @return the value in the specified column and row.
   */
  String getString(final int column, final int row);
}