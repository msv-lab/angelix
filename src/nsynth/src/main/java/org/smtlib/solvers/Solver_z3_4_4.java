/*
 * This file is part of the SMT project.
 * Copyright 2010 David R. Cok
 * Created August 2010
 */
package org.smtlib.solvers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.smtlib.IResponse;
import org.smtlib.SMT;
import org.smtlib.SolverProcess;
import org.smtlib.IExpr.IKeyword;
import org.smtlib.SMT.Configuration;
import org.smtlib.impl.Pos;

// Items not implemented:
//   attributed expressions
//   get-values get-assignment get-proof get-unsat-core
//   some error detection and handling

/** This class is an adapter that takes the SMT-LIB ASTs and translates them into Z3 commands */
public class Solver_z3_4_4 extends Solver_z3_4_3 {
    
    protected String NAME_VALUE = "z3-4.4";
    protected String AUTHORS_VALUE = "Leonardo de Moura and Nikolaj Bjorner";
    protected String VERSION_VALUE = "4.4";

	protected String cmds_win[] = new String[]{ "", "/smt2","/in"};//,"SMTLIB2_COMPLIANT=true"}; 
	protected String cmds_mac[] = new String[]{ "", "-smt2","-in","SMTLIB2_COMPLIANT=true"}; 
	protected String cmds_unix[] = new String[]{ "", "-smt2","-in"}; 

    public Solver_z3_4_4(Configuration smtConfig, String executable) {
        super(smtConfig, executable);
		if (isWindows) {
			cmds = cmds_win;
		} else if (isMac) {
			cmds = cmds_mac;
		} else {
			cmds = cmds_unix;
		}
		cmds[0] = executable;
//		double timeout = smtConfig.timeout;
//		if (timeout > 0) {
//			List<String> args = new java.util.ArrayList<String>(cmds.length+1);
//			args.addAll(Arrays.asList(cmds));
//			if (isWindows) args.add("/t:" + Double.toString(timeout));
//			else           args.add("-t:" + Double.toString(timeout));
//			cmds = args.toArray(new String[args.size()]);
//		}
		solverProcess = new SolverProcess(cmds,"\n",smtConfig.logfile);
		responseParser = new org.smtlib.sexpr.Parser(smt(),new Pos.Source("",null));
    }

	@Override
	public IResponse start() {
		try {
			solverProcess.start(false);
			// Note that these setup lines do alter the error line numbers for the user
			// FIXME - enable the following lines when the Z3 solver supports them
//			if (smtConfig.solverVerbosity > 0) solverProcess.sendNoListen("(set-option :verbosity ",Integer.toString(smtConfig.solverVerbosity),")");
//			if (!smtConfig.batch) solverProcess.sendNoListen("(set-option :interactive-mode true)"); // FIXME - not sure we can do this - we'll lose the feedback
			// Can't turn off printing success, or we get no feedback
			solverProcess.sendAndListen("(set-option :print-success true)\n"); // Z3 4.3.0 needs this because it mistakenly has the default for :print-success as false
			linesOffset ++; 
			//if (smtConfig.nosuccess) solverProcess.sendAndListen("(set-option :print-success false)");
			if (smtConfig.verbose != 0) smtConfig.log.logDiag("Started "+NAME_VALUE+" ");
			return smtConfig.responseFactory.success();
		} catch (Exception e) {
			return smtConfig.responseFactory.error("Failed to start process " + cmds[0] + " : " + e.getMessage());
		}
	}
	
    
	@Override
	public IResponse pop(int number) {
		if (!logicSet) {
			return smtConfig.responseFactory.error("The logic must be set before a pop command is issued");
		}
//		if (number < 0) throw new SMT.InternalException("Internal bug: A pop command called with a negative argument: " + number);
//		if (number > pushesDepth) return smtConfig.responseFactory.error("The argument to a pop command is too large: " + number + " vs. a maximum of " + (pushesDepth));
//		if (number == 0) return  successOrEmpty(smtConfig);
		try {
//			checkSatStatus = null;
//			pushesDepth -= number;
			return parseResponse(solverProcess.sendAndListen("(pop ",Integer.toString(number),")\n"));
		} catch (IOException e) {
			return smtConfig.responseFactory.error("Error writing to Z3 solver: " + e);
		}
	}

	@Override
	public IResponse push(int number) {
		if (!logicSet) {
			return smtConfig.responseFactory.error("The logic must be set before a push command is issued");
		}
//		if (number < 0) throw new SMT.InternalException("Internal bug: A push command called with a negative argument: " + number);
//		checkSatStatus = null;
//		if (number == 0) return smtConfig.responseFactory.success();
		try {
//			pushesDepth += number;
			IResponse r = parseResponse(solverProcess.sendAndListen("(push ",Integer.toString(number),")\n"));
			// FIXME - actually only see this problem on Linux
			if (r.isError() && !isWindows) return successOrEmpty(smtConfig);
			return r;
		} catch (Exception e) {
			return smtConfig.responseFactory.error("Error writing to Z3 solver: " + e);
		}
	}

//	@Override
//	public IResponse get_option(IKeyword key) {
//		IResponse r = sendCommand("(get-option " + key + ")");
//		if (key.toString().endsWith("channel")) {
//			if (r.toString().equals("stderr")) r = smtConfig.responseFactory.stringLiteral("stderr");
//			else if (r.toString().equals("stdout")) r = smtConfig.responseFactory.stringLiteral("stdout");
//		}
//		return r;
//	}
	

}
