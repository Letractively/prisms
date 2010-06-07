/*
 * DBUtils.java Created Nov 11, 2009 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * Contains database utility methods for general use
 */
public class DBUtils
{
	/**
	 * Translates betwen an SQL character type and a boolean
	 * 
	 * @param b The SQL character representing a boolean
	 * @return The boolean represented by the character
	 */
	public static boolean boolFromSql(String b)
	{
		return "t".equalsIgnoreCase(b);
	}

	/**
	 * Translates betwen an SQL character type and a boolean
	 * 
	 * @param b The boolean to be represented by a character
	 * @return The SQL character representing the boolean
	 */
	public static String boolToSql(boolean b)
	{
		return b ? "'t'" : "'f'";
	}

	/**
	 * Formats a generic string for entry into a database using SQL
	 * 
	 * @param str The general string to put into the database
	 * @return The string that should be appended to the SQL string
	 */
	public static String toSQL(String str)
	{
		if(str == null)
			return "NULL";
		return "'" + str.replaceAll("'", "''") + "'";
	}

	/** An expression to evaluate against an integer field in a database. */
	public static interface KeyExpression
	{
		/**
		 * @return The complexity of the expression
		 */
		int getComplexity();

		/**
		 * Generates the SQL where clause (without the "where") that allows selection of rows whose
		 * column matches this expression
		 * 
		 * @param column The name of the column to evaluate against this expression
		 * @return The SQL where clause
		 */
		String toSQL(String column);
	}

	/** Represents an expression that matches no rows */
	public static class NoneExpression implements KeyExpression
	{
		public int getComplexity()
		{
			return 2;
		}

		public String toSQL(String column)
		{
			return "(" + column + "=0 AND " + column + "=1)";
		}
	}

	/** Matches rows whose key matches a comparison expresion */
	public static class CompareExpression implements KeyExpression
	{
		/** The minimum value of the expression */
		public final long min;

		/** The maximum value of the expression */
		public final long max;

		/**
		 * Creates a CompareExpression
		 * 
		 * @param aMin The minimum value for the expression
		 * @param aMax The maximum value for the expression
		 */
		public CompareExpression(long aMin, long aMax)
		{
			min = aMin;
			max = aMax;
		}

		public int getComplexity()
		{
			int ret = 0;
			if(min > Long.MIN_VALUE)
			{
				ret++;
				if(max > Long.MIN_VALUE)
					ret += 2;
			}
			else if(max > Long.MIN_VALUE)
				ret++;
			return ret;
		}

		public String toSQL(String column)
		{
			String ret;
			if(min > Long.MIN_VALUE)
			{
				ret = column + ">=" + min;
				if(max > Long.MIN_VALUE)
					ret = "(" + ret + " AND " + column + "<=" + max + ")";
			}
			else if(max > Long.MIN_VALUE)
				ret = column + "<=" + max;
			else
				ret = "(" + column + "=0 AND " + column + "=1)";
			return ret;
		}
	}

	/** Matches rows whose keys are in a set */
	public static class ContainedExpression implements KeyExpression
	{
		/** The set of keys that this expression matches */
		public long [] theValues;

		public int getComplexity()
		{
			return theValues.length;
		}

		public String toSQL(String column)
		{
			String ret = column;
			if(theValues.length == 1)
				ret += "=" + theValues[0];
			else
			{
				ret += " IN (";
				for(int v = 0; v < theValues.length; v++)
				{
					ret += theValues[v];
					if(v < theValues.length - 1)
						ret += ", ";
				}
				ret += ")";
			}
			return ret;
		}
	}

	/** Matches rows that match any of a set of subexpressions */
	public static class OrExpression implements KeyExpression
	{
		/** The set of subexpressions */
		public KeyExpression [] exprs;

		void flatten()
		{
			int count = 0;
			for(int i = 0; i < exprs.length; i++)
			{
				if(exprs[i] instanceof NoneExpression)
					continue;
				else if(exprs[i] instanceof OrExpression)
					count += ((OrExpression) exprs[i]).exprs.length;
				else
					count++;
			}
			if(count == exprs.length)
				return;
			KeyExpression [] newExprs = new KeyExpression [count];
			int idx = 0;
			for(int i = 0; i < exprs.length; i++)
			{
				if(exprs[i] instanceof NoneExpression)
					continue;
				else if(exprs[i] instanceof OrExpression)
				{
					for(int j = 0; j < ((OrExpression) exprs[i]).exprs.length; j++, idx++)
						newExprs[idx] = ((OrExpression) exprs[i]).exprs[j];
				}
				else
					newExprs[idx++] = exprs[i];
			}
			exprs = newExprs;
		}

		public int getComplexity()
		{
			int ret = 0;
			for(int i = 0; i < exprs.length; i++)
			{
				ret += exprs[i].getComplexity();
				if(ret < exprs.length - 1)
					ret++;
			}
			return ret;
		}

		public String toSQL(String column)
		{
			String ret = "(";
			for(int i = 0; i < exprs.length; i++)
			{
				ret += exprs[i].toSQL(column);
				if(i < exprs.length - 1)
					ret += " OR ";
			}
			ret += ")";
			return ret;
		}
	}

	/**
	 * Compiles a set of keys into an expression that can be evaluated on a database more quickly
	 * and reliably than using an expression like "IN (id1, id2, ...)"
	 * 
	 * @param ids The set of IDs to compile into an expression
	 * @param maxComplexity The maximum complexity for the OR'ed expressions
	 * @return A single expression whose complexity<=maxComplexity, or an {@link OrExpression} whose
	 *         {@link OrExpression#exprs} are all <=maxComplexity
	 */
	public static KeyExpression simplifyKeySet(long [] ids, int maxComplexity)
	{
		// First we need to sort the list and remove duplicates
		java.util.Arrays.sort(ids);
		java.util.ArrayList<Integer> duplicates = new java.util.ArrayList<Integer>();
		for(int i = 1; i < ids.length; i++)
			if(ids[i] == ids[i - 1])
				duplicates.add(new Integer(i));
		if(duplicates.size() > 0)
		{
			long [] newIDs = new long [ids.length - duplicates.size()];
			int lastIdx = 0;
			for(int i = 0; i < duplicates.size(); i++)
			{
				int dup = duplicates.get(i).intValue();
				if(dup != lastIdx)
					System.arraycopy(ids, lastIdx, newIDs, lastIdx - i, dup - lastIdx);
				lastIdx = dup + 1;
			}
			if(lastIdx != ids.length)
				System.arraycopy(ids, lastIdx, newIDs, lastIdx - duplicates.size(), ids.length
					- lastIdx);
			ids = newIDs;
		}
		duplicates = null;
		KeyExpression ret = simplifyKeySet(ids);
		/* If ret is too complex, divide into multiple expressions of <=minComplexity that OR to
		 * make the same overall expression */
		return expand(ret, maxComplexity);
	}

	static KeyExpression simplifyKeySet(long [] ids)
	{
		if(ids[ids.length - 1] - ids[0] == ids.length - 1)
			return new CompareExpression(ids[0], ids[ids.length - 1]);
		int start = 0;
		java.util.ArrayList<Long> solos = new java.util.ArrayList<Long>();
		java.util.ArrayList<KeyExpression> ors = new java.util.ArrayList<KeyExpression>();
		for(int i = 1; i < ids.length; i++)
		{
			if(ids[i] != ids[i - 1] + 1)
			{
				if(i - start <= 2)
					for(int j = start; j < i; j++)
						solos.add(new Long(ids[j]));
				else
					ors.add(new CompareExpression(ids[start], ids[i - 1]));
				start = i;
			}
		}
		if(ids.length - start < 2)
			for(int j = start; j < ids.length; j++)
				solos.add(new Long(ids[j]));
		else
			ors.add(new CompareExpression(ids[start], ids[ids.length - 1]));
		KeyExpression ret;
		if(ors.size() > 1)
		{
			ret = new OrExpression();
			((OrExpression) ret).exprs = ors.toArray(new KeyExpression [ors.size()]);
		}
		else if(ors.size() == 1)
			ret = ors.get(0);
		else
			ret = new NoneExpression();
		if(solos.size() > 0)
		{
			ContainedExpression solosExpr = new ContainedExpression();
			solosExpr.theValues = new long [solos.size()];
			for(int i = 0; i < solos.size(); i++)
				solosExpr.theValues[i] = solos.get(i).longValue();
			OrExpression ret2 = new OrExpression();
			ret2.exprs = new KeyExpression [] {ret, solosExpr};
			ret = ret2;
		}
		return ret;
	}

	/**
	 * If the given expression is too complex, this method creates and returns an OrExpression whose
	 * sub-expressions are sufficiently simple.
	 * 
	 * @param expr The expression to expand
	 * @param maxComplexity The expanded expression which is either simple enough itself, or an
	 *        OrExpression whose sub-expressions are simple enough.
	 * @return The expanded expression
	 */
	public static KeyExpression expand(KeyExpression expr, int maxComplexity)
	{
		if(expr instanceof ContainedExpression)
		{
			ContainedExpression cont = (ContainedExpression) expr;
			int split = (cont.theValues.length - 1) / maxComplexity + 1;
			OrExpression ret = new OrExpression();
			ret.exprs = new KeyExpression [split];
			int idx = 0;
			for(int i = 0; i < split; i++)
			{
				int nextIdx = cont.theValues.length * (i + 1) / split;
				ContainedExpression ret_i = new ContainedExpression();
				ret.exprs[i] = ret_i;
				ret_i.theValues = new long [nextIdx - idx];
				for(int j = idx; j < nextIdx; j++)
					ret_i.theValues[j - idx] = cont.theValues[j];
			}
			return ret;
		}
		else if(expr instanceof OrExpression)
		{
			OrExpression boolExp = (OrExpression) expr;
			for(int i = 0; i < boolExp.exprs.length; i++)
				boolExp.exprs[i] = expand(boolExp.exprs[i], maxComplexity - 1);
			boolExp.flatten();
			return boolExp;
		}
		else
			return expr;
	}

	/**
	 * Internal testing method
	 * 
	 * @param args Command line args, ignored
	 */
	public static final void main(String [] args)
	{
		long [] ids = new long [] {1, 2, 3, 3, 4, 5, 6, 8, 8, 8, 8, 9, 9, 10, 10, 11, 12, 13, 14,
			20};
		simplifyKeySet(ids, 100);
	}
}
