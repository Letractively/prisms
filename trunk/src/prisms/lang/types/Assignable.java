/*
 * Assignable.java Created Nov 18, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

public abstract class Assignable extends prisms.lang.ParsedItem
{
	public abstract prisms.lang.EvaluationResult<?> getValue(prisms.lang.EvaluationEnvironment env,
		ParsedAssignmentOperator assign) throws prisms.lang.EvaluationException;

	public abstract void assign(Object value, prisms.lang.EvaluationEnvironment env,
		ParsedAssignmentOperator assign) throws prisms.lang.EvaluationException;
}
