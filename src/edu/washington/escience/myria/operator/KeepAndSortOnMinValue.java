package edu.washington.escience.myria.operator;

import edu.washington.escience.myria.Schema;
import edu.washington.escience.myria.TupleBatch;
import edu.washington.escience.myria.TupleBuffer;
import edu.washington.escience.myria.Type;
import edu.washington.escience.myria.column.Column;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntProcedure;

import java.util.BitSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

/**
 * Keeps min vaule. It adds newly meet unique tuples into a buffer so that the source TupleBatches are not referenced.
 * This implementation reduces memory consumption.
 * */
public final class KeepAndSortOnMinValue extends StreamingState {

  /** Required for Java serialization. */
  private static final long serialVersionUID = 1L;

  /**
   * The logger for this class.
   * */
  static final Logger LOGGER = LoggerFactory.getLogger(KeepAndSortOnMinValue.class.getName());

  /**
   * Indices to unique tuples.
   * */
  private transient TIntObjectMap<TIntList> uniqueTupleIndices;

  /**
   * The buffer for stroing unique tuples.
   * */
  private transient TupleBuffer uniqueTuples = null;

  /** column indices of the key. */
  private final int[] keyColIndices;
  /** column indices of the value. */
  private final int valueColIndex;

  /**
   * 
   * @param keyColIndices column indices of the key
   * @param valueColIndex column index of the value
   */
  public KeepAndSortOnMinValue(final int[] keyColIndices, final int valueColIndex) {
    this.keyColIndices = keyColIndices;
    this.valueColIndex = valueColIndex;
  }

  @Override
  public void cleanup() {
    uniqueTuples = null;
    uniqueTupleIndices = null;
  }

  /**
   * Check if a tuple in uniqueTuples equals to the comparing tuple (cntTuple).
   * 
   * @param index the index in uniqueTuples
   * @param column the source column
   * @param row the index of the source row
   * @return true if equals.
   * */
  private boolean shouldReplace(final int index, final Column<?> column, final int row) {
    Type t = column.getType();
    switch (t) {
      case INT_TYPE:
        return column.getInt(row) < uniqueTuples.getInt(valueColIndex, index);
      case FLOAT_TYPE:
        return column.getFloat(row) < uniqueTuples.getFloat(valueColIndex, index);
      case DOUBLE_TYPE:
        return column.getDouble(row) < uniqueTuples.getDouble(valueColIndex, index);
      case LONG_TYPE:
        return column.getLong(row) < uniqueTuples.getLong(valueColIndex, index);
      default:
        throw new IllegalStateException("type " + t + " is not supported in KeepMinValue.replace()");
    }
  }

  /**
   * Do duplicate elimination for tb.
   * 
   * @param tb the TupleBatch for performing DupElim.
   * @return the duplicate eliminated TB.
   * */
  protected TupleBatch keepMinValue(final TupleBatch tb) {
    final int numTuples = tb.numTuples();
    if (numTuples <= 0) {
      return tb;
    }
    doReplace.inputTB = tb;
    final List<Column<?>> columns = tb.getDataColumns();
    final BitSet toRemove = new BitSet(numTuples);
    for (int i = 0; i < numTuples; ++i) {
      final int nextIndex = uniqueTuples.numTuples();
      final int cntHashCode = tb.hashCode(i, keyColIndices);
      TIntList tupleIndexList = uniqueTupleIndices.get(cntHashCode);
      doReplace.unique = true;
      if (tupleIndexList == null) {
        tupleIndexList = new TIntArrayList();
        tupleIndexList.add(nextIndex);
        uniqueTupleIndices.put(cntHashCode, tupleIndexList);
      } else {
        doReplace.replaced = false;
        doReplace.row = i;
        tupleIndexList.forEach(doReplace);
        if (!doReplace.unique && !doReplace.replaced) {
          toRemove.set(i);
        }
      }
      if (doReplace.unique) {
        int inColumnRow = tb.getValidIndices().get(i);
        for (int j = 0; j < tb.numColumns(); ++j) {
          uniqueTuples.put(j, columns.get(j), inColumnRow);
        }
        tupleIndexList.add(nextIndex);
      }
    }
    return tb.remove(toRemove);
  }

  @Override
  public Schema getSchema() {
    return getOp().getSchema();
  }

  @Override
  public void init(final ImmutableMap<String, Object> execEnvVars) {
    uniqueTupleIndices = new TIntObjectHashMap<TIntList>();
    uniqueTuples = new TupleBuffer(getSchema());
    doReplace = new ReplaceProcedure();
  }

  @Override
  public TupleBatch update(final TupleBatch tb) {
    TupleBatch newtb = keepMinValue(tb);
    if (newtb.numTuples() > 0 || newtb.isEOI()) {
      return newtb;
    }
    return null;
  }

  @Override
  public List<TupleBatch> exportState() {
    TupleBuffer tmp = uniqueTuples.clone();
    sortOn(tmp, valueColIndex);
    return tmp.getAll();
  }

  /**
   * Traverse through the list of tuples and replace old values.
   * */
  private transient ReplaceProcedure doReplace;

  /**
   * Traverse through the list of tuples with the same hash code.
   * */
  private final class ReplaceProcedure implements TIntProcedure {

    /** row index of the tuple. */
    private int row;

    /** input TupleBatch. */
    private TupleBatch inputTB;

    /** if found a replacement. */
    private boolean replaced;

    /** if the given tuple doesn't exist. */
    private boolean unique;

    @Override
    public boolean execute(final int index) {
      if (inputTB.tupleEquals(row, uniqueTuples, index, keyColIndices, keyColIndices)) {
        unique = false;
        Column<?> valueColumn = inputTB.getDataColumns().get(valueColIndex);
        int inColumnRow = inputTB.getValidIndices().get(row);
        if (shouldReplace(index, valueColumn, inColumnRow)) {
          uniqueTuples.replace(valueColIndex, index, valueColumn, inColumnRow);
          replaced = true;
        }
      }
      return unique;
    }
  };

  /**
   * sort the given TukpleBuffer on a column.
   * 
   * @param tuples tuples
   * @param col column index
   */
  private void sortOn(final TupleBuffer tuples, final int col) {
    quicksort(tuples, col, 0, tuples.numTuples() - 1);
  }

  /**
   * quick sort on column col, tuple with smaller values are put in the front.
   * 
   * @param tuples tuples
   * @param col the column index
   * @param low lower bound
   * @param high upper bound
   */
  private void quicksort(final TupleBuffer tuples, final int col, final int low, final int high) {
    int i = low, j = high;
    int pivot = low + (high - low) / 2;

    while (i <= j) {
      while (compare(tuples, col, i, pivot) < 0) {
        i++;
      }
      while (compare(tuples, col, j, pivot) > 0) {
        j--;
      }
      if (i <= j) {
        if (i != j) {
          if (i == pivot) {
            pivot = j;
          } else if (j == pivot) {
            pivot = i;
          }
          for (int c = 0; c < tuples.numColumns(); ++c) {
            tuples.swap(c, i, j);
          }
        }
        i++;
        j--;
      }
    }
    if (low < j) {
      quicksort(tuples, col, low, j);
    }
    if (i < high) {
      quicksort(tuples, col, i, high);
    }
  }

  /**
   * compare a value in a column with pivot.
   * 
   * @param tuples tuples
   * @param column the column index
   * @param row row index to compare with
   * @param pivot the index of the pivot value
   * @return if the value is smaller than (-1), equal to (0) or bigger than (1) pivot
   */
  public int compare(final TupleBuffer tuples, final int column, final int row, final int pivot) {
    Type t = getSchema().getColumnType(column);
    switch (t) {
      case LONG_TYPE: {
        long tmp1 = tuples.getLong(column, row);
        long tmp2 = tuples.getLong(column, pivot);
        if (tmp1 < tmp2) {
          return -1;
        }
        if (tmp1 > tmp2) {
          return 1;
        }
        return 0;
      }
      case INT_TYPE: {
        int tmp1 = tuples.getInt(column, row);
        int tmp2 = tuples.getInt(column, pivot);
        if (tmp1 < tmp2) {
          return -1;
        }
        if (tmp1 > tmp2) {
          return 1;
        }
        return 0;
      }
      case DOUBLE_TYPE: {
        double tmp1 = tuples.getDouble(column, row);
        double tmp2 = tuples.getDouble(column, pivot);
        if (tmp1 < tmp2) {
          return -1;
        }
        if (tmp1 > tmp2) {
          return 1;
        }
        return 0;
      }
      case FLOAT_TYPE: {
        float tmp1 = tuples.getFloat(column, row);
        float tmp2 = tuples.getFloat(column, pivot);
        if (tmp1 < tmp2) {
          return -1;
        }
        if (tmp1 > tmp2) {
          return 1;
        }
        return 0;
      }
      default:
        throw new RuntimeException("compare() doesn't support type " + t);
    }
  }

  @Override
  public int numTuples() {
    return uniqueTuples.numTuples();
  }
}
