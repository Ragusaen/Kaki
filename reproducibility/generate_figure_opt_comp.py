
from resultParsing import *

all = handle_results_kaki('output/all')  # everything enabled
eqc = handle_results_kaki('output/eqc')  # equivalence classes (no topological decomposition)
td = handle_results_kaki('output/td')  # topological decomposition (no equivalence classes)
none = handle_results_kaki('output/none')  # neither equivalence classes nor topological decomposition
flip = handle_results_flip('output/flip')
netstack = handle_results_netstack('output/netstack')

def cactus(d: [TestData], only_every: int = 1):
    l = list(map(lambda x: x.fields[Measure.FlipDFATime] if x.fields[Measure.PreconditionFailed] else x.fields[Measure.TotalTime], filter(lambda x: x.fields[Measure.TotalTime] is not None or (x.fields[Measure.PreconditionFailed] is not None and x.fields[Measure.PreconditionFailed]), d)))
    l.sort()
    return ''.join(map(lambda x: str(x), list(filter(lambda x: x[0] % only_every == 0 or x[0] == len(l) - 1, list(enumerate(l))))))

skip = 50

# table = (
#     '\\documentclass{standalone}\n'
#     '\\usepackage[utf8]{inputenc}\n'
#     '\\usepackage{tikz}\n'
#     '\\usepackage{pgfplots}\n'
#     '\\begin{document}\n'
#     '\\begin{tikzpicture}\n'
#     '\\begin{axis}[\n'
#     'ylabel={Total time [s]}, legend pos= {north west}, legend style = {legend cell align=left}, ymode = {log}, tick label style={font=\\scriptsize}, minor y tick style = {draw = none}, y label style = {yshift = -5pt}, legend style = {font=\\scriptsize, row sep=-3pt}, width=\\linewidth, height=7cm,\n'
#     'xmin=0, xtick={0, 2000, 4000, 6000, 8000, 10000},xticklabels={0, 2000, 4000, 6000, 8000, 10000},\n'
#     'ymin=0.1,ymax=300, ytick={0.01,0.1,1,10,100}, yticklabels={0.01,0.1,1,10,100}, scaled ticks = false]\n'
#     '\\legend{All, Eq. classes, Decomposition, Baseline, FLIP}\n'
#     '\\addplot[mark=none, color=blue, thick] coordinates{\n'
#     f'{cactus(filter(lambda x: "MF" not in x.path, all), skip)}\n'
#     '};\n'
#     '\\addplot[mark=none, color=red, dashed, thick] coordinates{\n'
#     f'{cactus(filter(lambda x: "MF" not in x.path, eqc), skip)}\n'
#     '};\n'
#     '\\addplot[mark=none, color=orange, densely dotted, very thick] coordinates{\n'
#     f'{cactus(filter(lambda x: "MF" not in x.path, td), skip)}\n'
#     '};\n'
#     '\\addplot[mark=none, color=olive, dashdotdotted, thick] coordinates{\n'
#     f'{cactus(filter(lambda x: "MF" not in x.path, none), skip)}\n'
#     '};\n'
#     '\\addplot[mark=none, color=green, dashdotted, thick] coordinates{\n'
#     f'{cactus(flip, skip)}\n'
#     '};\n'
#     '\\end{axis}\n'
#     '\\end{tikzpicture}\n'
#     '\\end{document}\n'
# )

figure_kaki = (
    '\\documentclass{standalone}\n'
    '\\usepackage[utf8]{inputenc}\n'
    '\\usepackage{tikz}\n'
    '\\usepackage{pgfplots}\n'
    '\\begin{document}\n'
    '\\begin{tikzpicture}\n'
    '\\begin{axis}[\n'
    'ylabel={Total time [s]}, legend pos= {north west}, legend style = {legend cell align=left}, ymode = {log}, tick label style={font=\\scriptsize}, minor y tick style = {draw = none}, y label style = {yshift = -5pt}, legend style = {font=\\scriptsize, row sep=-3pt}, width=\\linewidth, height=7cm,\n'
    'xmin=0, xmax=8758, xtick={0, 2000, 4000, 6000, 8000, 10000},xticklabels={0, 2000, 4000, 6000, 8000, 10000},\n'
    'ymin=0.1,ymax=300, ytick={0.01,0.1,1,10,100}, yticklabels={0.01,0.1,1,10,100}, scaled ticks = false]\n'
    '\\legend{Kaki (all), Collective, Decomposition, Baseline}\n'
    '\\addplot[mark=none, color=blue, thick] coordinates{\n'
    f'{cactus(filter(lambda x: "MF" not in x.path, all), skip)}\n'
    '};\n'
    '\\addplot[mark=none, color=red, dashed, thick] coordinates{\n'
    f'{cactus(filter(lambda x: "MF" not in x.path, eqc), skip)}\n'
    '};\n'
    '\\addplot[mark=none, color=orange, densely dotted, very thick] coordinates{\n'
    f'{cactus(filter(lambda x: "MF" not in x.path, td), skip)}\n'
    '};\n'
    '\\addplot[mark=none, color=olive, dashdotdotted, thick] coordinates{\n'
    f'{cactus(filter(lambda x: "MF" not in x.path, none), skip)}\n'
    '};\n'
    '\\end{axis}\n'
    '\\end{tikzpicture}\n'
    '\\end{document}\n'
)

figure_wp1 = (
    '\\documentclass{standalone}\n'
    '\\usepackage[utf8]{inputenc}\n'
    '\\usepackage{tikz}\n'
    '\\usepackage{pgfplots}\n'
    '\\begin{document}\n'
    '\\begin{tikzpicture}\n'
    '\\begin{axis}[\n'
    'ylabel={Total time [s]}, legend pos= {north west}, legend style = {legend cell align=left}, ymode = {log}, tick label style={font=\\scriptsize}, minor y tick style = {draw = none}, y label style = {yshift = -5pt}, legend style = {font=\\scriptsize, row sep=-3pt}, width=\\linewidth, height=7cm,\n'
    'xmin=0, xmax=915, xtick={0, 100, 200, 300, 400, 500, 600, 700, 800, 900},xticklabels={0, 100, 200, 300, 400, 500, 600, 700, 800, 900},\n'
    'ymin=0.1,ymax=300, ytick={0.01,0.1,1,10,100}, yticklabels={0.01,0.1,1,10,100}, scaled ticks = false]\n'
    '\\legend{Kaki, FLIP, Netstack}\n'
    '\\addplot[mark=none, color=blue, thick] coordinates{\n'
    f'{cactus(filter(lambda x: "zoo_json_waypoint1" in x.path, all), 1)}\n'
    '};\n'
    '\\addplot[mark=none, color=green, dashed, thick] coordinates{\n'
    f'{cactus(filter(lambda x: "zoo_json_waypoint1" in x.path, flip), 1)}\n'
    '};\n'
    '\\addplot[mark=none, color=black, densely dotted, thick] coordinates{\n'
    f'{cactus(netstack, 1)}\n'
    '};\n'
    '\\end{axis}\n'
    '\\end{tikzpicture}\n'
    '\\end{document}\n'
)

figure_split_flows = (
    '\\documentclass{standalone}\n'
    '\\usepackage[utf8]{inputenc}\n'
    '\\usepackage{tikz}\n'
    '\\usepackage{pgfplots}\n'
    '\\begin{document}\n'
    '\\begin{tikzpicture}\n'
    '\\begin{axis}[\n'
    'ylabel={Total time [s]}, legend pos= {north west}, legend style = {legend cell align=left}, ymode = {log}, tick label style={font=\\scriptsize}, minor y tick style = {draw = none}, y label style = {yshift = -5pt}, legend style = {font=\\scriptsize, row sep=-3pt}, width=\\linewidth, height=7cm,\n'
    'xmin=0, xmax=855, xtick={0, 100, 200, 300, 400, 500, 600, 700, 800},xticklabels={0, 100, 200, 300, 400, 500, 600, 700, 800},\n'
    'ymin=0.1,ymax=300, ytick={0.01,0.1,1,10,100}, yticklabels={0.01,0.1,1,10,100}, scaled ticks = false]\n'
    '\\legend{Splittable forwarding, Nonsplittable forwarding}\n'
    '\\addplot[mark=none, color=blue, thick] coordinates{\n'
    f'{cactus(filter(lambda x: "zoo_json_MF" in x.path, all), 1)}\n'
    '};\n'
    '\\addplot[mark=none, color=red, dashed, thick] coordinates{\n'
    f'{cactus(filter(lambda x: "zoo_json_reachability" in x.path, all), 1)}\n'
    '};\n'
    '\\end{axis}\n'
    '\\end{tikzpicture}\n'
    '\\end{document}\n'
)

with open('figure_8.tex', 'w') as f:
    f.write(figure_kaki)
os.system('pdflatex figure_8.tex')
print(figure_kaki)

with open('figure_9.tex', 'w') as f:
    f.write(figure_wp1)
os.system('pdflatex figure_9.tex')
print(figure_wp1)

with open('figure_10.tex', 'w') as f:
    f.write(figure_split_flows)
os.system('pdflatex figure_10.tex')
print(figure_split_flows)