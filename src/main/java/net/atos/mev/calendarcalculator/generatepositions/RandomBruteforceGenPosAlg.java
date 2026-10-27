package net.atos.mev.calendarcalculator.generatepositions;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Tournament;

public class RandomBruteforceGenPosAlg {
	
	Environment env;
	
	public Tournament execute() {
		Tournament result = new Tournament(env);
		result.rounds[0].fillWithFirstRoundPos();
		for(int nRound=1; nRound < result.rounds.length; nRound++) {
			result.rounds[nRound].fillRandomlyForbidPreviousPos(result, nRound);
		}
		result.generateRandomMapPosToTeams();
		result.applyMapPosToTeams();
		return result;
	}

	public static void main(String[] args) {
		Environment env = new Environment();
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		RandomBruteforceGenPosAlg alg = new RandomBruteforceGenPosAlg();
		alg.env = env;
		Tournament result = alg.execute();
		System.out.println(result.toStringPos());
		System.out.println(result.toStringNames());
	}

}
