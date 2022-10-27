from resultParsing import *

all = handle_results_kaki('output/all')
flip = handle_results_flip('output/flip')

flipm = {td.path: td.fields for td in flip}

com = {td.path: {'kaki': td.fields, 'flip': flipm[td.path]} for td in all if 'MF' not in td.path}


def filter_com(_filter: str):
    return filter(lambda x: _filter in x[0], com.items())


def filter_has_measure(m: Measure, d):
    return filter(lambda x: x[1]['kaki'][m] is not None and x[1]['flip'][m] is not None, d)


def compareMoreBatches(_filter: str):
    return len(list(filter(lambda x: x[1]['kaki'][Measure.Batches] < x[1]['flip'][Measure.Batches], filter_has_measure(Measure.Batches, filter_com(_filter)))))


def kaki_only(_filter: str):
    return len(list(filter(lambda x: x[1]['kaki'][Measure.TotalTime] is not None and (x[1]['flip'][Measure.TotalTime] is None and not x[1]['flip'][Measure.PreconditionFailed]), filter_com(_filter))))


def flip_only(_filter: str):
    return len(list(filter(lambda x: (x[1]['flip'][Measure.TotalTime] is not None or x[1]['flip'][Measure.PreconditionFailed]) and x[1]['kaki'][Measure.TotalTime] is None, filter_com(_filter))))


def tagging(_filter: str):
    return len(list(filter(lambda x: x[1]['kaki'][Measure.Batches] is not None and x[1]['flip'][Measure.UsesTagAndMatch], filter_com(_filter))))


probs = ['reachability', 'json_waypoint1', 'json_waypoint2', 'json_waypoint4', 'json_waypoint8', 'alt_waypoint1', 'alt_waypoint2', 'alt_waypoint4', 'cond_enf1', 'cond_enf2', '']

table = (
    '\\documentclass{standalone}\n'
    '\\usepackage[utf8]{inputenc}\n'
    '\\usepackage{tikz}\n'
    '\\usepackage{pgfplots}\n'
    '\\pgfplotsset{compat=1.7}\n'
    '\\newcommand{\\tilt}[1]{\\multicolumn{1}{l}{\\rlap{\\rotatebox{60}{#1}}}}'
    '\\begin{document}\n'
    '\\centering\n'
    '\\setlength{\\tabcolsep}{0.2cm}\n'
    '\\begin{tabular}{c|c c c c c c c c c c c|c}\n'
    '& \\tilt{reachability} & \\tilt{1-wp} & \\tilt{2-wp} & \\tilt{4-wp} & \\tilt{8-wp} & \\tilt{1-alt-wp} & \\tilt{2-alt-wp} & \\tilt{4-alt-wp} & \\tilt{1-cond-enf} & \\tilt{2-cond-enf} & \\tilt{all} & \\tilt{percentage}\\\\\n'
    '\\hline\n'
    f'Total      & {" & ".join([str(len(list(filter_com(p)))) for p in probs])} & {str(round(len(list(filter_com(probs[-1]))) / len(list(filter_com(probs[-1]))) * 100, 1))} \\%\\\\\n'
    f'Only Kaki  & {" & ".join([str(kaki_only(p)) for p in probs])}  & {round(100 * kaki_only(probs[-1]) / len(list(filter_com(probs[-1]))), 1)}\\% \\\\\n'
    f'Only FLIP  & {" & ".join([str(flip_only(p)) for p in probs])} & {round(100 * flip_only(probs[-1]) / len(list(filter_com(probs[-1]))), 1)}\\%\\\\\n'
    f'Suboptimal & {" & ".join([str(compareMoreBatches(p)) for p in probs])} & {round(100 * compareMoreBatches(probs[-1]) / len(list(filter_com(probs[-1]))),1)}\\%\\\\\n'
    f'Tagging    & {" & ".join([str(tagging(p)) for p in probs])} & {round(100 * tagging(probs[-1]) / len(list(filter_com(probs[-1]))),1)}\\%\n'
    '\\end{tabular}\n'
    '\\end{document}\n')
    
with open('table.tex', 'w') as f:
    f.write(table)

os.system('pdflatex table.tex')

print(table)
