import os
from os import path
from collections.abc import Callable

from enum import Enum
from typing import Any

import re

class Measure(Enum):
    TotalTime = 1
    Batches = 2
    UpdatableSwitches = 3
    UsesTagAndMatch = 4
    ModelTooBig = 5
    M1TotalTime = 6
    Parallel = 7
    FlipTime = 8
    Decompositions = 9
    PreconditionFailed = 10
    FlipDFATime = 11


class TestData:
    def __init__(self, _path: str, _fields: [(Measure, Any)]):
        self.path = _path
        self.fields = {m: v for m, v in _fields}

    def __str__(self):
        return f"{self.path}:{self.fields}"

    def __repr__(self):
        return self.__str__()


def parse_results(output_dir: path, property_parsers) -> [TestData]:
    res = []
    for dir, dnames, fnames in os.walk(output_dir):
        for f in fnames:
            f: str
            if f.endswith('.txt'):
                raw = open(os.path.join(dir, f)).read()
                td = TestData(
                    os.path.relpath(os.path.join(dir, f), output_dir),
                    [pp(raw) for pp in property_parsers]
                )
                res.append(td)
    return res


def re_null(r, type):
    if r is None:
        return None
    if type == 'int':
        return int(r.group(1))
    if type == 'float':
        return float(r.group(1))
    if type == 'string':
        return r.group(1)


def fm(measure: Measure, regex: str, type: str):
    return lambda s: (measure, re_null(re.search(regex, s), type))


def kaki_total_time(s):
    tt = re.search(r"Total program runtime: (\d+\.\d+) seconds", s)
    st = re.search(r"sys (\d+\.\d+)", s)

    if tt is not None and st is not None:
        return float(tt.group(1)) - float(st.group(1))
    else:
        return None


def when_none(v, o):
    if v is None:
        return o
    else:
        return v


def handle_results_kaki(output_dir: path):
    return parse_results(output_dir, [
        fm(Measure.UpdatableSwitches, r"Switches to update: (\d+)", 'int'),
        fm(Measure.Batches, r"Minimum batches required: (\d+)", 'int'),
        lambda s: (Measure.TotalTime, kaki_total_time(s)),
        lambda s: (Measure.M1TotalTime,
                   sum(map(lambda x: float(x), re.findall(r'Subproblem verification (?:succeeded|failed) in (\d+\.\d+) seconds', s)))),
        fm(Measure.Decompositions, r"Decomposed topology into (\d+) subproblems", 'int'),
        lambda s: (Measure.PreconditionFailed, False)
    ])


def get_or_default(lst, i, d):
    if (i < 0 and len(lst) >= -i) or (i >= 0 and len(lst) > i):
        return lst[i]
    else:
        return d


def let(v, f):
    return f(v)


def flip_total_time(s):
    ft = re.search('Finished in +(\d+\.\d+) +seconds', s)
    kt = re.search('Flip subpaths generated in (\d+\.\d+) seconds!', s)

    if ft is not None and kt is not None:
        return float(ft.group(1)) + float(kt.group(1))
    else:
        return None


def handle_results_flip(output_dir: path):
    return parse_results(output_dir, [
        lambda s: (Measure.Batches, let(get_or_default(re.findall(r'\*STEP (\d+)\*', s), -1, None), lambda v: None if v is None else int(v))),
        fm(Measure.FlipTime, 'Finished in +(\d+\.\d+) +seconds', 'float'),
        lambda s: (Measure.TotalTime, flip_total_time(s)),
        lambda s: (Measure.UsesTagAndMatch, re.search('add-tag', s) is not None),
        lambda s: (Measure.ModelTooBig, re.search('Model too large for size-limited license', s) is not None),
        lambda s: (Measure.PreconditionFailed, re.search('FLIP.flip.PreconditionFailedError', s) is not None or re.search('FLIP.verify.InvalidNetwork', s) is not None),
        fm(Measure.FlipDFATime, r'Flip subpaths generated in (\d+\.\d+) seconds!', 'float')
    ])
    
def netstack_time(s: str):
    if 'returned non-zero exit status 137' in s or 'DUE TO TIME LIMIT' in s or s == "" or "out-of-memory" in s:
        return None
    return let(re_null(re.search(r'user (\d+\.\d+)', s), 'float'), lambda x: x if x is not None and x > 0 else 0.01)

def handle_results_netstack(output_dir: path):
    return parse_results(output_dir, [
        lambda s: (Measure.TotalTime, netstack_time(s)),
        lambda s: (Measure.PreconditionFailed, False)
    ])

def handle_results_seq21(output_dir: path):
    return parse_results(output_dir, [
        fm(Measure.TotalTime, r'Time \(seconds\) *: (\d\.\d+)', 'float')
    ])
