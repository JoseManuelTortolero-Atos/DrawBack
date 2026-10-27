#!/usr/bin/env python3
"""
Genera el seed Liquibase 2026-27 (context:seed) y la fixture de tests a partir de las
fuentes legacy del repositorio. NO se edita a mano: se regenera con

    pip install openpyxl
    python tools/seed/generate_seed.py

Fuentes (todas en el repo):
  * src/main/resources/sch*/…properties + Morocco*.properties  -> parámetros, equipos, reglas
  * src/main/resources/season/2026-27/schedule_*.csv            -> sorteo (se comprueba contra el
                                                                    'tournament' de los properties)
  * src/main/resources/sch*/2026-27/Calendar*-v0.xlsx           -> fechas colocadas, jornadas, orden
  * tools/seed/input/results-v0/*                               -> versión v0 APROBADA (partidos y ficheros)

Salidas:
  * src/main/resources/db/changelog/changes/002-seed-2026-27.sql
  * src/test/resources/seed/seed-2026-27.tsv   (misma información, para los tests Java sin BD)

Decisiones documentadas en docs/DB_MIGRATION.md:
  * Orden de fixtures = orden del Excel v0 para partidos ya colocados; resto, orden del sorteo.
  * GAP-04: SchMoroccoD2.properties numera teamsNotHomeSameDay desde .8; el parser legacy los
    ignoraba. El seed carga las 17 parejas.
  * D2 y F2 dependen de la v0 aprobada de D1 y F1 (include_dependency = true).
"""
import csv
import datetime as dt
import os
import sys

import openpyxl

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
RESULTS = os.path.join(ROOT, 'tools', 'seed', 'input', 'results-v0')
OUT_SQL = os.path.join(RES, 'db', 'changelog', 'changes', '002-seed-2026-27.sql')
OUT_TSV = os.path.join(ROOT, 'src', 'test', 'resources', 'seed', 'seed-2026-27.tsv')
SEASON = '2026-27'

COMPETITIONS = [
    # id, sch properties, schedule csv, v0 xlsx, results dir, outSample name, storage, suffix, dependsOn, sort
    ('BOTOLA_D1', 'schBotolaD1/SchMoroccoD1.properties', 'schedule_MoroccoBotola1_00113.csv',
     'schBotolaD1/2026-27/CalendarD1-v0.xlsx', 'schBotolaD1-v0', 'outSample.csv', 'schBotolaD1', '', None, 1),
    ('BOTOLA_D2', 'schBotolaD2/SchMoroccoD2.properties', 'schedule_MoroccoBotola2v1_00113.csv',
     'schBotolaD2/2026-27/CalendarD2-v0.xlsx', 'schBotolaD2-v0', 'outDatesD2.csv', 'schBotolaD2', 'D2', 'BOTOLA_D1', 2),
    ('CNPFF_D1', 'schCNPFF1/MoroccoCNPFF1.properties', 'schedule_MoroccoCNPFF1_00529.csv',
     'schCNPFF1/2026-27/CalendarF1-v0.xlsx', 'schCNPFF1-v0', 'outSampleCNPFF1.csv', 'schCNPFF1', 'CNPFF1', None, 3),
    ('CNPFF_D2', 'schCNPFF2/MoroccoCNPFF2.properties', 'schedule_MoroccoCNPFF2_00529.csv',
     'schCNPFF2/2026-27/CalendarF2-v0.xlsx', 'schCNPFF2-v0', 'outSampleCNPFF2.csv', 'schCNPFF2', 'CNPFF2', 'CNPFF_D1', 4),
]
NAMES = {'BOTOLA_D1': 'Botola Pro (1a Division)', 'BOTOLA_D2': 'Botola 2 (2a Division)',
         'CNPFF_D1': 'CNPFF (1a Division Femenina)', 'CNPFF_D2': 'CNPFF (2a Division Femenina)'}
EXTERNAL_NAMES = {'D1': 'Botola Pro D1', 'D2': 'Botola D2', 'F1': 'CNPFF D1', 'F2': 'CNPFF D2', 'CT': 'Coupe du Trône',
                  'CL': 'CAF Champions League', 'CLHOME': 'CAF Champions League (home)', 'CC': 'CAF Confederation Cup',
                  'CCHOME': 'CAF Confederation Cup (home)', 'ASC': 'Arab Super Cup', 'UAFA': 'UAFA Club Cup',
                  'UAFAHOME': 'UAFA Club Cup (home)', 'CHAN': 'CHAN', 'FIFA': 'FIFA window', 'AC': 'Arab Cup',
                  'WC': 'World Cup', 'ASL': 'Arab Super League', 'ASLHOME': 'Arab Super League (home)', 'CAN': 'AFCON'}


def load_props(path):
    """Lector de .properties compatible con java.util.Properties para estos ficheros."""
    props = {}
    with open(path, encoding='latin-1') as f:
        for raw in f:
            line = raw.strip()
            if not line or line[0] in '#!':
                continue
            for i, ch in enumerate(line):
                if ch in '=:':
                    props[line[:i].strip()] = line[i + 1:].strip()
                    break
    return props


def d(text):
    return dt.datetime.strptime(text.strip(), '%d/%m/%Y').date()


def legacy_pairs(props, prefix):
    """Semántica del legacy: lee desde .1 y para en el primer hueco."""
    out, i = [], 1
    while props.get(f'{prefix}.{i}') is not None:
        out.append(props[f'{prefix}.{i}'].split(','))
        i += 1
    return out


def all_pairs(props, prefix):
    """Corrección GAP-04: todas las claves numeradas, en orden numérico."""
    keys = sorted((int(k[len(prefix) + 1:]), k) for k in props if k.startswith(prefix + '.') and k[len(prefix) + 1:].isdigit())
    return [props[k].split(',') for _, k in keys]


def read_tournament(env_props, tournament):
    pos_part, teams_part = tournament.split(';')
    codes = teams_part.strip(',').split(',')
    n = int(env_props['nTeams'])
    per_round = n // 2
    rounds, idx = [], 0
    for _ in range(int(env_props['nRounds'])):
        rnd = []
        for _ in range(per_round):
            a, b = int(pos_part[idx:idx + 2]), int(pos_part[idx + 2:idx + 4])
            rnd.append((codes[a - 1], codes[b - 1]))
            idx += 4
        rounds.append(rnd)
    return rounds


def read_xlsx_rows(path):
    wb = openpyxl.load_workbook(path, data_only=True)
    rows = []
    for row in wb.worksheets[0].iter_rows(min_row=2, max_col=7, values_only=True):
        vals = []
        for j, v in enumerate(row):
            if v is None:
                v = ''
            if j == 1 and isinstance(v, float):
                v = str(int(v))
            vals.append(str(v).strip())
        if vals[0]:
            rows.append(vals)
    return rows


def read_result_rows(path):
    out = []
    with open(path, encoding='utf-8') as f:
        for line in f:
            p = line.rstrip('\r\n').split(',')
            if len(p) < 7 or p[0] in ('sep=', 'Competition') or not p[5]:
                continue
            out.append({'competition': p[0], 'round': p[1], 'date': d(p[3]), 'hour': p[4] or None, 'home': p[5], 'away': p[6] or None})
    return out


def build(cid, sch_file, csv_file, xlsx_file, res_dir, out_name, storage, suffix, depends, sort):
    p = load_props(os.path.join(RES, sch_file))
    env = load_props(os.path.join(RES, p['env']))
    code = env['code']
    n = int(env['nTeams'])
    teams = [(env[f'team.{i}'], env.get('team.' + env[f'team.{i}'], env[f'team.{i}'])) for i in range(n)]
    first_leg = read_tournament(env, p['tournament'])

    # El CSV del sorteo debe coincidir con el 'tournament' (fuente que usaba el motor legacy)
    with open(os.path.join(RES, 'season', SEASON, csv_file), encoding='utf-8') as f:
        csv_rows = [r for r in csv.reader(f) if r and r[0].isdigit()]
    for r in range(len(first_leg)):
        from_csv = [(x[1], x[2]) for x in csv_rows if int(x[0]) == r + 1]
        assert sorted(from_csv) == sorted(first_leg[r]), f'{cid}: draw CSV != tournament in round {r + 1}'
        mirror = sorted((b, a) for a, b in first_leg[r])
        assert sorted((x[1], x[2]) for x in csv_rows if int(x[0]) == r + 1 + len(first_leg)) == mirror, f'{cid}: CSV second leg is not a mirror'

    rows = read_xlsx_rows(os.path.join(RES, xlsx_file))
    placed, round_dates, external = {}, {}, []
    for r in rows:
        if r[0] == code:
            if r[5] and r[6] and r[1]:
                placed[(int(r[1]), r[5], r[6])] = (d(r[3]), r[4] or None)
            elif r[1]:
                round_dates.setdefault(r[1], [])
                if d(r[3]) not in round_dates[r[1]]:
                    round_dates[r[1]].append(d(r[3]))
        else:
            external.append(r)

    fixtures = []
    for rr in range(2 * len(first_leg)):
        rno = rr + 1
        leg = first_leg[rr % len(first_leg)]
        matches = leg if rr < len(first_leg) else [(b, a) for a, b in leg]
        placed_here = [k for k in placed if k[0] == rno]   # orden del Excel
        for k in placed_here:
            date, hour = placed[k]
            fixtures.append({'round': rno, 'home': k[1], 'away': k[2], 'locked': True, 'date': date, 'hour': hour})
        for h, a in matches:
            if (rno, h, a) not in placed:
                fixtures.append({'round': rno, 'home': h, 'away': a, 'locked': False, 'date': None, 'hour': None})
    assert len(fixtures) == 2 * len(first_leg) * (n // 2)

    # Externos: los de la liga de la que se depende NO se guardan (vienen de su versión aprobada)
    dep_code = None
    if depends:
        dep_code = {'BOTOLA_D1': 'D1', 'CNPFF_D1': 'F1'}[depends]
    ext = [{'competition': r[0], 'round': r[1] or None, 'date': d(r[3]), 'hour': r[4] or None,
            'home': r[5] or None, 'away': r[6] or None} for r in external if r[0] != dep_code]
    dep_rows_in_excel = [{'competition': r[0], 'round': r[1] or None, 'date': d(r[3]), 'hour': r[4] or None,
                          'home': r[5] or None, 'away': r[6] or None} for r in external if r[0] == dep_code]

    competitions = p['ScheduleMoroccoAlg.competitions'].split(',')
    rest = [(c, int(p[f'ScheduleMoroccoAlg.restDays.{c}'])) for c in competitions]
    groups, i = [], 1
    while f'ScheduleMoroccoAlg.assignGroups.{i}' in p:
        groups.append((p[f'ScheduleMoroccoAlg.assignGroups.{i}'], int(p[f'ScheduleMoroccoAlg.roundsForwardLook.{i}'])))
        i += 1
    no_light = set(x for x in p['ScheduleMoroccoAlg.teamsWithoutLight'].split(',') if x)
    optimize = p['ScheduleMoroccoAlg.teamsToOptimizeTimes'].split(',')
    # Se guardan como flags por equipo: el motor solo usa estas listas como conjuntos
    # (HashMap / mínimos y máximos; teamsWithoutLight se ordena y se busca con binarySearch).
    assert set(optimize) <= {t for t, _ in teams}, f'{cid}: optimizeTimes with unknown team'
    assert no_light <= {t for t, _ in teams}, f'{cid}: teamsWithoutLight with unknown team'

    legacy = legacy_pairs(p, 'ScheduleMoroccoAlg.teamsNotHomeSameDay')
    fixed = all_pairs(p, 'ScheduleMoroccoAlg.teamsNotHomeSameDay')
    derbies, rules = [], []
    for k in range(1, int(env.get('nChecks', '0')) + 1):
        c = env.get(f'check.{k}')
        if not c:
            continue
        parts = c.split(',')
        if parts[0] == 'DerbyTeamsCheck':
            derbies.append(parts[1:3])
        elif parts[0] == 'MatchNotInRoundCheck':
            rules.append({'type': 'MatchNotInRound', 'a': parts[1], 'b': parts[2], 'rounds': ','.join(parts[3:]), 'definition': c})
        elif parts[0] == 'SeparateMatchesCheck':
            rules.append({'type': 'SeparateMatches', 'teams': ','.join(parts[2:]), 'min': int(parts[1]), 'definition': c})
        else:
            rules.append({'type': 'LEGACY_CHECK', 'definition': c})

    versions = read_result_rows(os.path.join(RESULTS, res_dir, out_name))
    files = sorted(os.listdir(os.path.join(RESULTS, res_dir)))
    slots = p['ScheduleMoroccoAlg.timeslots'].split(',')
    return {
        'id': cid, 'code': code, 'name': NAMES[cid], 'storage': storage, 'suffix': suffix, 'sort': sort, 'depends': depends,
        'teams': teams, 'no_light': no_light, 'optimize': set(optimize), 'slots': slots,
        'start': d(p['start']), 'end': d(p['end']), 'second_leg': d(p['ScheduleMoroccoAlg.startSecondLeg']),
        'max_per_day': int(p['ScheduleMoroccoAlg.maxMatchesPerDay']),
        'adv': int(p['ScheduleMoroccoAlg.maxDaysAdvanceMatch']), 'post': int(p['ScheduleMoroccoAlg.maxDaysPostponeMatch']),
        'time_look': int(p['ScheduleMoroccoAlg.timeAssignsForwardLook']), 'last': int(p['ScheduleMoroccoAlg.lastRoundToAssign']),
        'ram_start': d(p['ScheduleMoroccoAlg.ramadan.start']), 'ram_end': d(p['ScheduleMoroccoAlg.ramadan.end']),
        'ram_slot': p.get('ScheduleMoroccoAlg.ramadan.timeslots'), 'no_light_slot': p.get('ScheduleMoroccoAlg.timeslotWithoutLight'),
        'groups_to_run': int(p['ScheduleMoroccoAlg.groupsToRun']), 'group_opt': int(p['ScheduleMoroccoAlg.groupToOptimizeTimes']),
        'maj': p['ScheduleMoroccoAlg.groupsCanGoToMAJ'], 'order': p['ScheduleMoroccoAlg.roundCalculationOrder'],
        'groups': groups, 'rest': rest, 'pairs': fixed, 'legacy_pairs': legacy, 'derbies': derbies, 'rules': rules,
        'rounds': round_dates, 'fixtures': fixtures, 'external': ext, 'dep_rows_in_excel': dep_rows_in_excel,
        'v0': versions, 'v0_dir': res_dir, 'v0_files': files, 'colours': {c: p.get(f'ScheduleMoroccoAlg.colour.{c}') for c in competitions},
    }


def q(v):
    if v is None:
        return 'NULL'
    if isinstance(v, bool):
        return 'TRUE' if v else 'FALSE'
    if isinstance(v, int):
        return str(v)
    if isinstance(v, dt.date):
        return f"DATE '{v.isoformat()}'"
    return "'" + str(v).replace("'", "''") + "'"


def content_type(name):
    if name.endswith('.xlsx'):
        return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    if name.endswith('.zip'):
        return 'application/zip'
    return 'text/csv'


def main():
    comps = [build(*c) for c in COMPETITIONS]
    by_id = {c['id']: c for c in comps}
    # D2/F2: las filas de la liga dependiente en el Excel v0 = versión v0 aprobada de esa liga
    for c in comps:
        if c['depends']:
            dep = by_id[c['depends']]
            dep_rows = [r for r in dep['v0'] if r['competition'] == dep['code']]
            got = [(r['date'], r['hour'], r['home'], r['away']) for r in c['dep_rows_in_excel']]
            exp = [(r['date'], r['hour'], r['home'], r['away']) for r in dep_rows]
            assert got == exp, f"{c['id']}: dependency rows in Excel differ from {dep['id']} v0"

    sql = ['--liquibase formatted sql', '',
           '-- GENERADO por tools/seed/generate_seed.py. No editar a mano.',
           '--changeset calendar:010-seed-2026-27 context:seed',
           f"INSERT INTO season (id) VALUES ({q(SEASON)});"]
    tsv = ['# GENERADO por tools/seed/generate_seed.py (misma información que 002-seed-2026-27.sql)']

    for c in comps:
        sql.append(f"INSERT INTO competition (id, code, name, storage_dir, output_suffix, sort_order) VALUES "
                   f"({q(c['id'])}, {q(c['code'])}, {q(c['name'])}, {q(c['storage'])}, {q(c['suffix'])}, {c['sort']});")
    team_names = {}
    for c in comps:
        for code, name in c['teams']:
            team_names.setdefault(code, name)
    for code in sorted(team_names):
        sql.append(f"INSERT INTO team (code, name) VALUES ({q(code)}, {q(team_names[code])});")
    ext_codes = []
    for c in comps:
        for comp, _ in c['rest']:
            if comp not in ext_codes:
                ext_codes.append(comp)
    colours = {}
    for c in comps:
        for k, v in c['colours'].items():
            if v and k != c['code']:
                colours.setdefault(k, v)
    for code in ext_codes:
        sql.append(f"INSERT INTO external_competition (code, name, colour) VALUES ({q(code)}, {q(EXTERNAL_NAMES.get(code, code))}, {q(colours.get(code))});")

    for c in comps:
        cs = f"(SELECT id FROM competition_season WHERE competition_id = {q(c['id'])} AND season_id = {q(SEASON)})"
        first_round = min([f['date'] for f in c['fixtures'] if f['date']] + [x for v in c['rounds'].values() for x in v], default=None)
        sql.append('')
        sql.append(f"-- {c['id']} ({c['code']})")
        sql.append("INSERT INTO competition_season (competition_id, season_id, season_start, season_end, first_round_date, start_second_leg, "
                   "max_matches_per_day, max_days_advance_match, max_days_postpone_match, allow_maj, rounds_forward_look, time_assigns_forward_look, "
                   "last_round_to_assign, matchday_pattern, ramadan_start, ramadan_end, ramadan_slot, no_light_slot, groups_to_run, "
                   "group_to_optimize_times, groups_can_go_to_maj, round_calculation_order, depends_on_competition_id, include_dependency) VALUES ("
                   f"{q(c['id'])}, {q(SEASON)}, {q(c['start'])}, {q(c['end'])}, {q(first_round)}, {q(c['second_leg'])}, {c['max_per_day']}, "
                   f"{c['adv']}, {c['post']}, {q('1' in c['maj'].split(','))}, {c['groups'][0][1]}, {c['time_look']}, {c['last']}, 'sat-sun', "
                   f"{q(c['ram_start'])}, {q(c['ram_end'])}, {q(c['ram_slot'])}, {q(c['no_light_slot'])}, {c['groups_to_run']}, {c['group_opt']}, "
                   f"{q(c['maj'])}, {q(c['order'])}, {q(c['depends'])}, {q(c['depends'] is not None)});")
        tsv.append('\t'.join(['CS', c['id'], c['code'], c['name'], SEASON, c['start'].isoformat(), c['end'].isoformat(),
                              first_round.isoformat() if first_round else '', c['second_leg'].isoformat(), str(c['max_per_day']), str(c['adv']),
                              str(c['post']), c['maj'], str(c['groups'][0][1]), str(c['time_look']), str(c['last']),
                              c['ram_start'].isoformat(), c['ram_end'].isoformat(), c['ram_slot'] or '', c['no_light_slot'] or '',
                              str(c['groups_to_run']), str(c['group_opt']), c['order'], c['depends'] or '', c['storage'], c['suffix']]))
        for i, (code, name) in enumerate(c['teams']):
            sql.append(f"INSERT INTO competition_season_team (competition_season_id, team_code, list_order, display_name, no_light, optimize_times) "
                       f"VALUES ({cs}, {q(code)}, {i}, {q(name)}, {q(code in c['no_light'])}, {q(code in c['optimize'])});")
            tsv.append('\t'.join(['TEAM', c['id'], code, name, str(code in c['no_light']).lower(), str(code in c['optimize']).lower()]))
        for i, s in enumerate(c['slots']):
            sql.append(f"INSERT INTO timeslot (competition_season_id, list_order, slot) VALUES ({cs}, {i}, {q(s)});")
            tsv.append('\t'.join(['SLOT', c['id'], s]))
        for i, (teams, look) in enumerate(c['groups']):
            sql.append(f"INSERT INTO assign_group (competition_season_id, group_no, teams, rounds_forward_look) VALUES ({cs}, {i + 1}, {q(teams)}, {look});")
            tsv.append('\t'.join(['GROUP', c['id'], teams, str(look)]))
        for i, (comp, days) in enumerate(c['rest']):
            sql.append(f"INSERT INTO rest_rule (competition_season_id, list_order, competition_code, rest_days) VALUES ({cs}, {i}, {q(comp)}, {days});")
            tsv.append('\t'.join(['REST', c['id'], comp, str(days)]))
        for i, (a, b) in enumerate(c['pairs']):
            sql.append(f"INSERT INTO team_pair_rule (competition_season_id, kind, list_order, team_a, team_b) VALUES ({cs}, 'NOT_HOME_SAME_DAY', {i}, {q(a)}, {q(b)});")
            tsv.append('\t'.join(['PAIR', c['id'], a, b]))
        for i, (a, b) in enumerate(c['derbies']):
            sql.append(f"INSERT INTO team_pair_rule (competition_season_id, kind, list_order, team_a, team_b) VALUES ({cs}, 'DERBY', {i}, {q(a)}, {q(b)});")
            tsv.append('\t'.join(['DERBY', c['id'], a, b]))
        for i, r in enumerate(c['rules']):
            sql.append("INSERT INTO constraint_rule (competition_season_id, list_order, rule_type, team_a, team_b, teams, rounds, min_rounds, definition, note) VALUES "
                       f"({cs}, {i}, {q(r['type'])}, {q(r.get('a'))}, {q(r.get('b'))}, {q(r.get('teams'))}, {q(r.get('rounds'))}, {q(r.get('min'))}, "
                       f"{q(r['definition'])}, 'Legacy draw check (P1); informative for the planner (P2)');")
            tsv.append('\t'.join(['RULE', c['id'], r['type'], r.get('a') or '', r.get('b') or '', r.get('teams') or '', r.get('rounds') or '',
                                  str(r.get('min') or ''), r['definition']]))
        ordered_rounds = sorted(c['rounds'].items(), key=lambda kv: kv[1][0])
        for i, (label, dates) in enumerate(ordered_rounds):
            rno = int(label) if label.isdigit() else None
            nr = len(c['fixtures']) // (len(c['teams']) // 2) // 2
            leg = None if rno is None else (1 if rno <= nr else 2)
            sql.append(f"INSERT INTO league_round (competition_season_id, list_order, round_no, label, leg) VALUES ({cs}, {i}, {q(rno)}, {q(None if rno else label)}, {q(leg)});")
            for dd in dates:
                sql.append(f"INSERT INTO round_date (league_round_id, play_date) SELECT id, {q(dd)} FROM league_round WHERE competition_season_id = {cs} AND list_order = {i};")
            tsv.append('\t'.join(['ROUND', c['id'], label, ','.join(x.isoformat() for x in dates)]))
        for i, f in enumerate(c['fixtures']):
            sql.append(f"INSERT INTO fixture (competition_season_id, round_no, list_order, home, away, locked, play_date, play_hour) VALUES "
                       f"({cs}, {f['round']}, {i}, {q(f['home'])}, {q(f['away'])}, {q(f['locked'])}, {q(f['date'])}, {q(f['hour'])});")
            tsv.append('\t'.join(['FIX', c['id'], str(f['round']), f['home'], f['away'], str(f['locked']).lower(),
                                  f['date'].isoformat() if f['date'] else '', f['hour'] or '']))
        for i, e in enumerate(c['external']):
            sql.append(f"INSERT INTO external_commitment (competition_season_id, list_order, competition_code, round_label, play_date, play_hour, home, away) VALUES "
                       f"({cs}, {i}, {q(e['competition'])}, {q(e['round'])}, {q(e['date'])}, {q(e['hour'])}, {q(e['home'])}, {q(e['away'])});")
            tsv.append('\t'.join(['EXT', c['id'], e['competition'], e['round'] or '', e['date'].isoformat(), e['hour'] or '', e['home'] or '', e['away'] or '']))
        # versión v0 aprobada = resultado legacy importado
        sql.append("INSERT INTO generation_version (competition_season_id, version_no, status, last_round_to_assign, source, approved_at, approved_by) VALUES "
                   f"({cs}, 0, 'APPROVED', {c['last']}, 'LEGACY_IMPORT', now(), 'seed');")
        vid = f"(SELECT id FROM generation_version WHERE competition_season_id = {cs} AND version_no = 0)"
        for i, m in enumerate(c['v0']):
            sql.append(f"INSERT INTO generated_match (version_id, list_order, competition_code, round_label, play_date, play_hour, home, away) VALUES "
                       f"({vid}, {i}, {q(m['competition'])}, {q(m['round'])}, {q(m['date'])}, {q(m['hour'])}, {q(m['home'])}, {q(m['away'])});")
            tsv.append('\t'.join(['V0', c['id'], m['competition'], m['round'] or '', m['date'].isoformat(), m['hour'] or '', m['home'], m['away'] or '']))
        for name in c['v0_files']:
            data = open(os.path.join(RESULTS, c['v0_dir'], name), 'rb').read()
            sql.append(f"INSERT INTO generation_file (version_id, name, content_type, size_bytes, content) VALUES "
                       f"({vid}, {q(name)}, {q(content_type(name))}, {len(data)}, decode('{data.hex()}', 'hex'));")
    sql.append("--rollback DELETE FROM generation_file; DELETE FROM generated_match; DELETE FROM generation_version; DELETE FROM external_commitment; "
               "DELETE FROM fixture; DELETE FROM round_date; DELETE FROM league_round; DELETE FROM constraint_rule; DELETE FROM team_pair_rule; "
               "DELETE FROM rest_rule; DELETE FROM assign_group; DELETE FROM timeslot; DELETE FROM competition_season_team; DELETE FROM competition_season; "
               "DELETE FROM external_competition; DELETE FROM team; DELETE FROM competition; DELETE FROM season;")
    os.makedirs(os.path.dirname(OUT_SQL), exist_ok=True)
    os.makedirs(os.path.dirname(OUT_TSV), exist_ok=True)
    with open(OUT_SQL, 'w', encoding='utf-8') as f:
        f.write('\n'.join(sql) + '\n')
    with open(OUT_TSV, 'w', encoding='utf-8') as f:
        f.write('\n'.join(tsv) + '\n')
    for c in comps:
        print(f"{c['id']}: teams={len(c['teams'])} fixtures={len(c['fixtures'])} placed={sum(1 for f in c['fixtures'] if f['date'])} "
              f"rounds-with-dates={len(c['rounds'])} external={len(c['external'])} pairs={len(c['pairs'])} (legacy parser: {len(c['legacy_pairs'])}) "
              f"v0-matches={len(c['v0'])} files={len(c['v0_files'])}")
    print('written', os.path.relpath(OUT_SQL, ROOT), 'and', os.path.relpath(OUT_TSV, ROOT))


if __name__ == '__main__':
    sys.exit(main())
