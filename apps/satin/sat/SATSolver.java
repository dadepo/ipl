// File: $Id$

/**
 * A parallel SAT solver. Given a symbolic boolean equation in CNF, find a set
 * of assignments that make this equation true.
 * 
 * This implementation tries to do all the things a professional SAT
 * solver would do, although we are limited by implementation time and
 * the fact that we need to parallelize the stuff.
 * 
 * @author Kees van Reeuwijk
 * @version $Revision$
 */

import java.io.File;

public final class SATSolver extends ibis.satin.SatinObject implements SATInterface, java.io.Serializable {
    private static final boolean traceSolver = false;
    private static final boolean printSatSolutions = true;
    private static final boolean traceNewCode = true;
    private static int label = 0;

    /**
     * The maximum remaining solver depth before this problem
     * is turned over to the (more lightweight, and non-parallel)
     * leaf solver.
     */
    private static final int leafSolverDepth = 8;

    /**
     * Solve the leaf part of a SAT problem.
     * The method throws a SATResultException if it finds a solution,
     * or terminates normally if it cannot find a solution.
     * @param level branching level
     * @param p the SAT problem to solve
     * @param ctx the changable context of the solver
     * @param var the next variable to assign
     * @param val the value to assign
     */
    public void leafSolve(
	int level,
	SATProblem p,
	SATContext ctx,
	int var,
	boolean val
    ) throws SATResultException
    {
	if( traceSolver ){
	    System.err.println( "ls" + level + ": trying assignment var[" + var + "]=" + val );
	}
	ctx.assignments[var] = val?1:0;
	int res;
	if( val ){
	    res = ctx.propagatePosAssignment( p, var );
	}
	else {
	    res = ctx.propagateNegAssignment( p, var );
	}
	if( res == -1 ){
	    // Propagation reveals a conflict.
	    if( traceSolver ){
		System.err.println( "ls" + level + ": propagation found a conflict" );
	    }
	    return;
	}
	if( res == 1 ){
	    // Propagation reveals problem is satisfied.
	    SATSolution s = new SATSolution( ctx.assignments );

	    if( traceSolver | printSatSolutions ){
		System.err.println( "ls" + level + ": propagation found a solution: " + s );
	    }
	    if( !p.isSatisfied( ctx.assignments ) ){
		System.err.println( "Error: " + level + ": solution does not satisfy problem." );
	    }
	    throw new SATResultException( s );
	}
	int nextvar = ctx.getDecisionVariable();
	if( nextvar<0 ){
	    // There are no variables left to assign, clearly there
	    // is no solution.
	    if( traceSolver ){
		System.err.println( "ls" + level + ": nothing to branch on" );
	    }
	    return;
	}

	boolean firstvar = ctx.posDominant( nextvar );
	SATContext subctx = (SATContext) ctx.clone();
	leafSolve( level+1, p, subctx, nextvar, firstvar );
	// Since we won't be using our context again, we may as well
	// give it to the recursion.
	// Also note that this call is a perfect candidate for tail
	// call elimination.
	leafSolve( level+1, p, ctx, nextvar, !firstvar );
    }

    /**
     * The method that implements a Satin task.
     * The method throws a SATResultException if it finds a solution,
     * or terminates normally if it cannot find a solution.
     * @param level branching level
     * @param p the SAT problem to solve
     * @param ctx the changable context of the solver
     * @param var the next variable to assign
     * @param val the value to assign
     */
    public void solve(
	int level,
	SATProblem p,
	SATContext ctx,
	int var,
	boolean val
    ) throws SATResultException
    {
	if( traceSolver ){
	    System.err.println( "s" + level + ": trying assignment var[" + var + "]=" + val );
	}
	ctx.assignments[var] = val?1:0;
	int res;
	if( val ){
	    res = ctx.propagatePosAssignment( p, var );
	}
	else {
	    res = ctx.propagateNegAssignment( p, var );
	}
	if( res == -1 ){
	    // Propagation reveals a conflict.
	    if( traceSolver ){
		System.err.println( "s" + level + ": propagation found a conflict" );
	    }
	    return;
	}
	if( res == 1 ){
	    // Propagation reveals problem is satisfied.
	    SATSolution s = new SATSolution( ctx.assignments );

	    if( traceSolver | printSatSolutions ){
		System.err.println( "s" + level + ": propagation found a solution: " + s );
	    }
	    if( !p.isSatisfied( ctx.assignments ) ){
		System.err.println( "Error: " + level + ": solution does not satisfy problem." );
	    }
	    throw new SATResultException( s );
	}
	int nextvar = ctx.getDecisionVariable();
	if( nextvar<0 ){
	    // There are no variables left to assign, clearly there
	    // is no solution.
	    if( traceSolver ){
		System.err.println( "s" + level + ": nothing to branch on" );
	    }
	    return;
	}

        boolean firstvar = ctx.posDominant( nextvar );
        if( level>30 || ctx.unsatisfied<100 ){
	    //System.err.println( "s" + level + " depth: " + depth + " unsat: " + ctx.unsatisfied + " dsat: " + dsat );
	    // We're nearly there, use the leaf solver.
	    // We have variable 'nextvar' to branch on.
	    SATContext subctx = (SATContext) ctx.clone();
	    leafSolve( level+1, p, subctx, nextvar, firstvar );
	    subctx = (SATContext) ctx.clone();
	    leafSolve( level+1, p, subctx, nextvar, !firstvar );
	}
	else {
	    // We have variable 'nextvar' to branch on.
	    SATContext firstctx = (SATContext) ctx.clone();
	    solve( level+1, p, firstctx, nextvar, firstvar );
	    SATContext secondctx = (SATContext) ctx.clone();
	    solve( level+1, p, secondctx, nextvar, !firstvar );
	    sync();
	}
    }

    /**
     * Given a SAT problem, returns a solution, or <code>null</code> if
     * there is no solution.
     * @param p The problem to solve.
     * @return a solution of the problem, or <code>null</code> if there is no solution
     */
    static SATSolution solveSystem( final SATProblem p )
    {
	SATSolution res = null;

	if( p.isConflicting() ){
	    return null;
	}
	if( p.isSatisfied() ){
	    return new SATSolution( p.buildInitialAssignments() );
	}
        SATSolver s = new SATSolver();

        // Now recursively try to find a solution.
	try {
	    int var = p.getMFUVariable();

	    if( traceSolver ){
		System.err.println( "Top level: branching on variable " + var );
	    }
	    if( var == -1 ){
		return null;
	    }
	    SATContext ctx = SATContext.buildSATContext( p );

	    ctx.assignments = p.buildInitialAssignments();

	    int r = ctx.optimize( p );
	    if( r == 1 ){
		if( !p.isSatisfied( ctx.assignments ) ){
		    System.err.println( "Error: solution does not satisfy problem." );
		}
		return new SATSolution( ctx.assignments );
	    }
	    if( r == -1 ){
		return null;
	    }

	    int nextvar = ctx.getDecisionVariable();
	    if( nextvar<0 ){
		// There are no variables left to assign, clearly there
		// is no solution.
		if( traceSolver | traceNewCode ){
		    System.err.println( "top: nothing to branch on" );
		}
		return null;
	    }

	    SATContext negctx = (SATContext) ctx.clone();
	    boolean firstvar = ctx.posDominant( nextvar );
	    s.solve( 0, p, negctx, nextvar, firstvar );
	    s.solve( 0, p, ctx, nextvar, !firstvar );
	    s.sync();
	}
	catch( SATResultException r ){
	    if( r.s == null ){
		System.err.println( "A null solution thrown???" );
	    }
	    res = r.s;
	    s.abort();
	}

	return res;
    }

    /**
     * Allows execution of the class.
     * @param args The command-line arguments.
     */
    public static void main( String args[] ) throws java.io.IOException
    {
	if( args.length != 1 ){
	    System.err.println( "Exactly one filename argument required." );
	    System.exit( 1 );
	}
	File f = new File( args[0] );
	if( !f.exists() ){
	    System.err.println( "File does not exist: " + f );
	    System.exit( 1 );
	}
	SATProblem p = SATProblem.parseDIMACSStream( f );
	p.report( System.out );
	p.optimize();
	p.report( System.out );
	long startTime = System.currentTimeMillis();
	SATSolution res = solveSystem( p );

	long endTime = System.currentTimeMillis();
	double time = ((double) (endTime - startTime))/1000.0;

	System.out.println( "Time: " + time );
	if( res == null ){
	    System.out.println( "There are no solutions" );
	}
	else {
	    System.out.println( "There is a solution: " + res );
	}
    }
}
