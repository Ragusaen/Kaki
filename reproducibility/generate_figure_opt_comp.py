
from resultParsing import *

all = handle_results_kaki('output/all')  # everything enabled
eqc = handle_results_kaki('output/eqc')  # equivalence classes (no topological decomposition)
td = handle_results_kaki('output/td')  # topological decomposition (no equivalence classes)
none = handle_results_kaki('output/none')  # neither equivalence classes nor topological decomposition
flip = handle_results_flip('output/flip')


def cactus(d: [TestData], only_every: int = 1):
    l = list(filter(lambda x: x is not None, map(lambda x: x.fields[Measure.TotalTime], d)))
    l.sort()
    return ''.join(map(lambda x: str(x), list(filter(lambda x: x[0] % only_every == 0 or x[0] == len(l) - 1, list(enumerate(l))))))


table = (
    '\\begin{tikzpicture}\n'
    '\\begin{axis}[\n'
    'ylabel={Total time [s]}, legend pos= {north west}, legend style = {legend cell align=left}, ymode = {log}, tick label style={font=\\scriptsize}, minor y tick style = {draw = none}, y label style = {yshift = -5pt}, legend style = {font=\\scriptsize, row sep=-3pt}, width=\\linewidth, height=7cm,\n'
    'xmin=-50, xtick={0, 2000, 4000, 6000, 8000, 10000},xticklabels={0, 2000, 4000, 6000, 8000, 10000},\n'
    'ymin=0.1,ymax=300, ytick={0.01,0.1,1,10,100}, yticklabels={0.01,0.1,1,10,100}, scaled ticks = false]\n'
    '\\legend{All, Eq. classes, Decomposition, Baseline, FLIP}\n'
    '\\addplot[mark=none, color=blue, thick] coordinates{\n'
    f'{cactus(filter(lambda x: "MF" not in x.path, all), 50)}\n'
    '};\n'
    '\\addplot[mark=none, color=red, dashed, thick] coordinates{\n'
    f'{cactus(filter(lambda x: "MF" not in x.path, eqc), 50)}\n'
    '};\n'
    '\\addplot[mark=none, color=orange, densely dotted, very thick] coordinates{\n'
    f'{cactus(filter(lambda x: "MF" not in x.path, td), 50)}\n'
    '};\n'
    '\\addplot[mark=none, color=olive, dashdotdotted, thick] coordinates{\n'
    f'{cactus(filter(lambda x: "MF" not in x.path, none), 50)}\n'
    '};\n'
    '\\addplot[mark=none, color=green, dashdotted, thick] coordinates{\n'
    f'{cactus(flip, 50)}\n'
    '};\n'
    '\\end{axis}\n'
    '\\end{tikzpicture}\n'
)

print(table)