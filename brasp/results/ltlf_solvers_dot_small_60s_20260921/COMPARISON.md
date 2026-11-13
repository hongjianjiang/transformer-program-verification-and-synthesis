7 instances, 60 s budget (wall time incl. export)

| solver | decided | PAR-2 (s) | total on common | median on common | wrong |
| --- | ---: | ---: | ---: | ---: | ---: |
| aalta | 6/7 | 139 | 19.3 | 0.52 | 0 |
| black | 6/7 | 161 | 41.2 | 1.10 | 0 |
| lisa | 7/7 | 7 | 4.8 | 0.75 | 0 |
| lydia | 7/7 | 63 | 19.8 | 0.97 | 0 |

common instances (decided by all): 6
ranking by PAR-2: lisa < lydia < aalta < black

| family | param | aalta | black | lisa | lydia | fastest |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| dot | 5 | 0.4 | 0.5 | 0.4 | 0.5 | aalta |
| dot | 10 | 0.4 | 0.5 | 0.5 | 0.5 | aalta |
| dot | 20 | 0.5 | 0.7 | 0.6 | 0.6 | aalta |
| dot | 30 | 0.6 | 1.5 | 0.9 | 1.3 | aalta |
| dot | 40 | 1.2 | 5.4 | 1.0 | 3.1 | lisa |
| dot | 60 | 16.2 | 32.6 | 1.4 | 13.7 | lisa |
| dot | 80 | TO | TO | 1.8 | 43.5 | lisa |

fastest-on-instance counts: aalta 4, black 0, lisa 3, lydia 0
