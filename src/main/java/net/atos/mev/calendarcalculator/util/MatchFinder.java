package net.atos.mev.calendarcalculator.util;

/**
 * Clase de ejemplo con el método findMatch con MUCHO debug logging
 * Este archivo es solo para referencia y demostración
 */
public class MatchFinder {

    // Simulación de estructuras (ajusta según tu proyecto real)
    private Round[] rounds;
    private Object env;

    public Match findMatch(int round, String teamHome, String teamAway) {
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         🔍 INICIANDO BÚSQUEDA DE PARTIDO                                  ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝");
        System.out.println("📋 PARÁMETROS DE ENTRADA:");
        System.out.println("   → Ronda solicitada: " + round);
        System.out.println("   → Equipo Local (teamHome): '" + teamHome + "'");
        System.out.println("   → Equipo Visitante (teamAway): '" + teamAway + "'");
        System.out.println("   → Total de rondas disponibles: " + this.rounds.length);
        System.out.println("");

        if(round >= this.rounds.length) {
            System.out.println("╔═══════════════════════════════════════════════════════════════════════════╗");
            System.out.println("║  🔄 DETECTADO: Búsqueda de SEGUNDA VUELTA (PARTIDO DE VUELTA)            ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝");
            System.out.println("⚠️  La ronda " + round + " es mayor o igual que " + this.rounds.length);
            System.out.println("💡 Esto significa que estamos buscando el partido de VUELTA");
            System.out.println("🔍 Estrategia: Buscar el partido de IDA e INVERTIR los equipos");
            
            int firstLegRound = round - this.rounds.length;
            System.out.println("📍 Calculando ronda de IDA: " + round + " - " + this.rounds.length + " = " + firstLegRound);
            System.out.println("🔎 Buscando en la ronda de IDA (round " + firstLegRound + ")...");
            System.out.println("");

            System.out.println("🏃 Iterando sobre los partidos de la ronda " + firstLegRound + ":");
            System.out.println("────────────────────────────────────────────────────────────────────────────");
            
            int matchIndex = 0;
            for(Match m : this.rounds[firstLegRound].matches) {
                matchIndex++;
                System.out.println("   🎯 Analizando partido #" + matchIndex + " de la ronda " + firstLegRound);
                
                if(m == null) {
                    System.out.println("      ⚠️  Partido es NULL, saltando al siguiente...");
                    continue;
                }
                
                String team2Code = m.getTeam2().getCode();
                String team1Code = m.getTeam1().getCode();
                
                System.out.println("      📊 Datos del partido de IDA encontrado:");
                System.out.println("         • Equipo 1 (Local en IDA): " + m.getTeam1().getName() + " (código: '" + team1Code + "')");
                System.out.println("         • Equipo 2 (Visitante en IDA): " + m.getTeam2().getName() + " (código: '" + team2Code + "')");
                System.out.println("");
                System.out.println("      🔄 Lógica de VUELTA: Invertir equipos");
                System.out.println("         • Local en VUELTA debe ser: '" + teamHome + "'");
                System.out.println("         • Visitante en VUELTA debe ser: '" + teamAway + "'");
                System.out.println("");
                System.out.println("      🔍 Verificando coincidencias INVERTIDAS:");
                System.out.println("         • ¿Team2 ('" + team2Code + "') es igual a teamHome ('" + teamHome + "')? " + team2Code.equals(teamHome));
                System.out.println("         • ¿Team1 ('" + team1Code + "') es igual a teamAway ('" + teamAway + "')? " + team1Code.equals(teamAway));
                
                if(m.getTeam2().getCode().equals(teamHome) && m.getTeam1().getCode().equals(teamAway)) {
                    System.out.println("");
                    System.out.println("╔═══════════════════════════════════════════════════════════════════════════╗");
                    System.out.println("║  ✅ ¡PARTIDO DE VUELTA ENCONTRADO! - COINCIDENCIA PERFECTA               ║");
                    System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝");
                    System.out.println("🎉 El partido de IDA:");
                    System.out.println("   " + m.getTeam1().getName() + " (local) vs " + m.getTeam2().getName() + " (visitante)");
                    System.out.println("");
                    System.out.println("🔄 Se convierte en el partido de VUELTA:");
                    System.out.println("   " + m.getTeam2().getName() + " (local) vs " + m.getTeam1().getName() + " (visitante)");
                    System.out.println("");
                    System.out.println("🏗️  Creando nuevo objeto Match con:");
                    System.out.println("   • pos1 = " + m.getPos2() + " (era pos2 en IDA)");
                    System.out.println("   • pos2 = " + m.getPos1() + " (era pos1 en IDA)");
                    System.out.println("   • team1 = " + m.getTeam2().getName() + " (era team2 en IDA)");
                    System.out.println("   • team2 = " + m.getTeam1().getName() + " (era team1 en IDA)");
                    
                    Match returnMatch = new Match(m.getPos2(), m.getPos1(), env);
                    returnMatch.setTeam1(m.getTeam2());
                    returnMatch.setTeam2(m.getTeam1());
                    
                    System.out.println("✅ Partido de VUELTA creado exitosamente");
                    System.out.println("🔙 RETORNANDO partido de vuelta");
                    System.out.println("═══════════════════════════════════════════════════════════════════════════");
                    return returnMatch;
                } else {
                    System.out.println("      ❌ NO hay coincidencia, continuando búsqueda...");
                }
                System.out.println("────────────────────────────────────────────────────────────────────────────");
            }
            
            System.out.println("");
            System.out.println("⚠️  NO se encontró el partido de vuelta en la ronda de IDA " + firstLegRound);
            
        } else {
            System.out.println("╔═══════════════════════════════════════════════════════════════════════════╗");
            System.out.println("║  ⚽ DETECTADO: Búsqueda de PRIMERA VUELTA (PARTIDO DE IDA)               ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝");
            System.out.println("✅ La ronda " + round + " es menor que " + this.rounds.length);
            System.out.println("💡 Buscamos directamente en el array de partidos de la ronda " + round);
            System.out.println("");
            System.out.println("🏃 Iterando sobre los partidos de la ronda " + round + ":");
            System.out.println("────────────────────────────────────────────────────────────────────────────");
            
            int matchIndex = 0;
            for(Match m : this.rounds[round].matches) {
                matchIndex++;
                System.out.println("🔍 Analizando partido #" + matchIndex + " en ronda: " + round);
                
                if(m != null) {
                    System.out.println("   ✅ Partido NO es null, procediendo con análisis...");
                    System.out.println("");
                    System.out.println("   📊 Datos del partido encontrado:");
                    System.out.println("      • Equipo 1 (Local): " + m.getTeam1().getName());
                    System.out.println("         └─ Código: '" + m.getTeam1().getCode() + "'");
                    System.out.println("      • Equipo 2 (Visitante): " + m.getTeam2().getName());
                    System.out.println("         └─ Código: '" + m.getTeam2().getCode() + "'");
                    System.out.println("");
                    System.out.println("   🔍 Comparando con los equipos solicitados:");
                    System.out.println("      • Equipo Local buscado: '" + teamHome + "'");
                    System.out.println("      • Equipo Visitante buscado: '" + teamAway + "'");
                    System.out.println("");
                    System.out.println("   🎯 Verificación de coincidencias:");
                    
                    boolean team1Matches = m.getTeam1().getCode().equals(teamHome);
                    boolean team2Matches = m.getTeam2().getCode().equals(teamAway);
                    
                    System.out.println("      • ¿Team1 ('" + m.getTeam1().getCode() + "') == teamHome ('" + teamHome + "')? " + 
                                     (team1Matches ? "✅ SÍ" : "❌ NO"));
                    System.out.println("      • ¿Team2 ('" + m.getTeam2().getCode() + "') == teamAway ('" + teamAway + "')? " + 
                                     (team2Matches ? "✅ SÍ" : "❌ NO"));
                    System.out.println("");
                    
                    if(team1Matches && team2Matches) {
                        System.out.println("╔═══════════════════════════════════════════════════════════════════════════╗");
                        System.out.println("║  🎊 ¡PARTIDO DE IDA ENCONTRADO! - COINCIDENCIA TOTAL                     ║");
                        System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝");
                        System.out.println("✨ Partido encontrado en ronda " + round + ":");
                        System.out.println("   🏠 Local: " + m.getTeam1().getName() + " (" + m.getTeam1().getCode() + ")");
                        System.out.println("   ✈️  Visitante: " + m.getTeam2().getName() + " (" + m.getTeam2().getCode() + ")");
                        System.out.println("");
                        System.out.println("🔙 RETORNANDO partido encontrado directamente (sin modificaciones)");
                        System.out.println("═══════════════════════════════════════════════════════════════════════════");
                        return m;
                    } else {
                        System.out.println("   ❌ Los códigos NO coinciden, continuando búsqueda...");
                    }
                    
                } else {
                    System.out.println("   ⚠️  Partido #" + matchIndex + " es NULL, saltando...");
                }
                System.out.println("────────────────────────────────────────────────────────────────────────────");
            }
            
            System.out.println("");
            System.out.println("⚠️  NO se encontró el partido solicitado en la ronda " + round);
        }
        
        System.out.println("");
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  ❌ RESULTADO FINAL: PARTIDO NO ENCONTRADO                                ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝");
        System.out.println("📌 Parámetros de búsqueda:");
        System.out.println("   • Ronda: " + round);
        System.out.println("   • Equipo Local: '" + teamHome + "'");
        System.out.println("   • Equipo Visitante: '" + teamAway + "'");
        System.out.println("");
        System.out.println("💡 Posibles razones:");
        System.out.println("   1. Los códigos de equipo no existen en esa ronda");
        System.out.println("   2. Los equipos están invertidos");
        System.out.println("   3. La ronda especificada no tiene ese enfrentamiento");
        System.out.println("   4. Hay un error en los códigos de equipo");
        System.out.println("");
        System.out.println("🔙 RETORNANDO NULL");
        System.out.println("═══════════════════════════════════════════════════════════════════════════");
        
        return null;
    }

    // Clases auxiliares de ejemplo (ajusta según tu proyecto)
    private static class Round {
        Match[] matches;
    }

    private static class Match {
        private Team team1;
        private Team team2;
        private int pos1;
        private int pos2;

        public Match(int pos1, int pos2, Object env) {
            this.pos1 = pos1;
            this.pos2 = pos2;
        }

        public Team getTeam1() { return team1; }
        public void setTeam1(Team team) { this.team1 = team; }
        public Team getTeam2() { return team2; }
        public void setTeam2(Team team) { this.team2 = team; }
        public int getPos1() { return pos1; }
        public int getPos2() { return pos2; }
    }

    private static class Team {
        private String code;
        private String name;

        public String getCode() { return code; }
        public String getName() { return name; }
    }
}
