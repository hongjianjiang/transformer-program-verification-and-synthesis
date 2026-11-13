# Figure 3: all baseline comparison

102 instances; 900 s end-to-end per repetition; 1 repetition(s) requested for remaining cases. Completed measurements are retained; each timing is the median of its recorded samples.
NuSMV used up to 2 concurrent engine workers. The LTL-circuit route uses one repetition and ABC PDR. Metadata records protocol changes. Larger family members after a timeout or size limit are explicitly skipped.

| Engine | Decided | Timeout | Size limit | Bound limit | Skipped | Other/pending |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct + ABC | 99 | 0 | 0 | 0 | 2 | 1 |
| Aalta | 67 | 13 | 0 | 0 | 17 | 5 |
| Lisa | 46 | 8 | 2 | 0 | 44 | 2 |
| Elimination + ABC | 88 | 4 | 0 | 0 | 10 | 0 |
| Elimination + NuSMV | 52 | 10 | 0 | 0 | 40 | 0 |
| LTL + NuSMV SAT-BMC | 34 | 7 | 2 | 0 | 59 | 0 |
| Direct + NuSMV | 81 | 9 | 0 | 0 | 11 | 1 |
| Circuit + NuSMV $k$-induction | 57 | 5 | 0 | 0 | 40 | 0 |

| Instance | Direct + ABC | Aalta | Lisa | Elimination + ABC | Elimination + NuSMV | LTL + NuSMV SAT-BMC | Direct + NuSMV | Circuit + NuSMV $k$-induction |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| dot__100 | 0.500 | 426.800 | 3.000 | 1.162 | 13.689 | timeout | 1.867 | 50.151 |
| dot__400 | 0.600 | timeout | 31.800 | 2.102 | timeout | skipped | 34.581 | timeout |
| dot__800 | 0.800 | timeout | 673.400 | 7.532 | skipped | skipped | 503.008 | skipped |
| dot__3200 | 1.700 | timeout | 823.100 | 107.970 | skipped | skipped | timeout | skipped |
| Y__10 | 0.500 | 0.400 | 0.400 | 0.986 | 1.032 | 1.303 | 0.712 | 0.736 |
| Y__12 | 0.500 | 0.500 | 0.400 | 0.898 | 1.439 | 0.807 | 0.893 | 0.847 |
| Y__14 | 0.500 | 0.500 | 0.400 | 0.818 | 0.840 | 0.726 | 0.632 | 0.717 |
| Y__16 | 0.600 | 0.600 | 0.400 | 0.978 | 0.833 | 0.833 | 0.723 | 0.911 |
| Y__20 | 0.500 | 0.900 | 0.400 | 0.853 | 0.833 | 0.929 | 0.641 | 0.769 |
| Y__100 | 0.500 | 5.200 | 0.500 | 1.055 | 1.544 | 41.087 | 1.040 | 17.203 |
| Y__200 | 0.600 | 24.600 | 0.800 | 1.065 | 3.951 | 855.589 | 3.500 | 231.478 |
| Y__300 | 0.500 | 122.800 | 1.000 | 1.225 | 8.179 | timeout | 9.996 | timeout |
| Y__500 | 0.800 | timeout | 1.700 | 1.369 | 28.685 | skipped | 39.929 | skipped |
| Y__8000 | 3.700 | timeout | 134.400 | 21.707 | timeout | skipped | timeout | skipped |
| no2a__5 | 0.500 | 0.500 | 0.400 | 0.956 | 0.840 | 0.827 | 0.422 | 0.728 |
| no2a__8 | 0.500 | 0.500 | 0.500 | 0.965 | 0.859 | 0.762 | 0.458 | 0.658 |
| no2a__12 | 0.500 | 0.600 | 1.000 | 1.032 | 0.890 | 0.787 | 0.450 | 0.858 |
| no2a__16 | 0.500 | 0.600 | 12.500 | 0.933 | 0.865 | 0.882 | 0.456 | 0.672 |
| no2a__24 | 0.500 | 0.600 | error | 0.957 | 0.979 | 0.705 | 0.441 | 0.585 |
| no2a__200 | 0.500 | 0.600 | timeout | 1.126 | 1.369 | 0.838 | 0.562 | 0.638 |
| no2a__600 | 0.500 | 5.300 | timeout | 1.405 | 6.477 | 1.023 | 0.990 | 0.830 |
| no2a__1000 | 0.700 | 54.200 | skipped | 1.967 | 17.225 | 1.373 | 1.914 | 0.852 |
| no2a__4000 | 0.700 | timeout | skipped | 6.003 | 393.560 | 4.927 | 39.706 | 1.431 |
| no2a__64000 | 4.300 | timeout | skipped | timeout | timeout | timeout | timeout | 32.750 |
| mono__8 | 0.500 | 3.024 | 3.432 | 1.402 | 3.097 | timeout | 0.883 | 0.505 |
| mono__9 | 0.500 | 3.381 | 4.575 | 1.312 | 3.550 | skipped | 0.499 | 0.521 |
| mono__10 | 0.600 | 2.481 | 5.040 | 1.133 | 2.257 | skipped | 0.554 | 0.537 |
| mono__11 | 0.500 | 1.714 | 12.920 | 1.019 | 1.874 | skipped | 0.617 | 0.509 |
| mono__12 | 0.500 | 1.786 | 10.957 | 0.993 | 1.916 | skipped | 0.480 | 0.533 |
| mono__16 | 0.500 | 2.462 | 49.574 | 1.281 | 5.102 | skipped | 0.499 | 0.623 |
| mono__32 | 0.700 | 2.380 | timeout | 1.787 | timeout | skipped | 0.672 | 0.876 |
| mono__128 | 3.900 | 52.876 | skipped | 8.074 | skipped | skipped | 3.896 | 18.816 |
| mono__256 | 29.100 | 404.220 | skipped | 48.495 | skipped | skipped | 23.108 | 193.077 |
| since__2 | 0.500 | 1.214 | 1.073 | 1.125 | 1.003 | 1.225 | 0.480 | 0.440 |
| since__4 | 0.567 | 1.004 | 1.061 | 0.999 | 0.935 | 0.703 | 0.614 | 0.479 |
| since__6 | 0.569 | 1.054 | 1.146 | 0.951 | 1.007 | 0.908 | 0.477 | 0.512 |
| since__8 | 0.543 | 1.035 | 2.480 | 0.945 | 1.228 | 1.452 | 0.459 | 0.515 |
| since__12 | 0.546 | 1.172 | 390.132 | 0.993 | 1.414 | 189.905 | 0.505 | 0.473 |
| since__16 | 0.563 | 1.259 | timeout | 1.034 | 5.435 | size_limit | 0.484 | 0.537 |
| since__32 | 0.716 | 1.423 | skipped | 1.231 | timeout | skipped | 0.592 | 0.601 |
| since__128 | 1.600 | 25.097 | skipped | 1.659 | skipped | skipped | 1.299 | 1.291 |
| since__256 | 2.200 | 350.785 | skipped | 2.531 | skipped | skipped | 3.423 | 3.486 |
| slb__2 | 0.500 | 0.924 | 0.932 | 0.978 | 1.398 | 0.486 | 0.451 | 0.489 |
| slb__4 | 0.500 | 0.956 | 0.918 | 0.952 | 1.141 | 0.570 | 0.451 | 0.467 |
| slb__6 | 0.500 | 0.999 | 0.988 | 0.976 | 1.074 | 0.668 | 0.463 | 0.513 |
| slb__8 | 0.500 | 1.370 | 2.616 | 1.039 | 1.147 | 1.045 | 0.446 | 0.506 |
| slb__12 | 0.500 | 1.065 | 257.988 | 1.036 | 1.493 | 30.616 | 0.466 | 0.509 |
| slb__16 | 0.500 | 1.001 | timeout | 1.083 | 3.017 | size_limit | 0.494 | 0.505 |
| slb__32 | 0.500 | 1.159 | skipped | 1.139 | timeout | skipped | 0.566 | 0.649 |
| slb__128 | 1.000 | 18.173 | skipped | 1.466 | skipped | skipped | 1.291 | 1.260 |
| slb__256 | 2.339 | 272.623 | skipped | 2.250 | skipped | skipped | 3.589 | 3.289 |
| marks__200 | 0.600 | timeout | size_limit | 1.307 | 7.606 | timeout | 2.713 | 114.503 |
| marks__600 | 1.000 | timeout | size_limit | 3.088 | timeout | skipped | 56.102 | timeout |
| marks__800 | 0.800 | timeout | timeout | 5.387 | skipped | skipped | 306.465 | skipped |
| marks__1200 | 1.300 | timeout | skipped | 11.806 | skipped | skipped | timeout | skipped |
| marks__2400 | 2.000 | timeout | skipped | 45.489 | skipped | skipped | skipped | skipped |
| same__300 | 0.800 | timeout | error | 2.325 | 193.990 | timeout | 7.986 | timeout |
| same__400 | 0.700 | skipped | skipped | 1.949 | 587.539 | skipped | 16.724 | skipped |
| same__500 | 0.700 | skipped | skipped | 2.809 | timeout | skipped | 60.650 | skipped |
| same__600 | 0.900 | skipped | skipped | 3.233 | skipped | skipped | 123.968 | skipped |
| same__1200 | 1.100 | skipped | skipped | 9.798 | skipped | skipped | timeout | skipped |
| lms__1 | 0.500 | 1.655 | 4.383 | 0.775 | 1.399 | 1.226 | 0.785 | 0.931 |
| lms__2 | 0.500 | 0.853 | timeout | 0.858 | 1.404 | 1.152 | 0.593 | 0.510 |
| lms__3 | 0.500 | 1.412 | skipped | 0.940 | 1.297 | 1.666 | 0.564 | 0.521 |
| lms__4 | 0.500 | 0.995 | skipped | 0.886 | 2.150 | 1.959 | 0.989 | 0.539 |
| lms__5 | 0.500 | 1.224 | skipped | 1.190 | 231.620 | 3.839 | 0.534 | 0.688 |
| lms__6 | 0.600 | 1.839 | skipped | 1.304 | timeout | 10.330 | 0.626 | 0.770 |
| lms__7 | 0.700 | 2.941 | skipped | 1.753 | skipped | 57.316 | 1.048 | 1.158 |
| lms__8 | 0.800 | 7.778 | skipped | 3.007 | skipped | 372.187 | 2.649 | 2.152 |
| lms__9 | 1.100 | 17.981 | skipped | 7.284 | skipped | timeout | 16.816 | 4.005 |
| lms__10 | 1.700 | 86.389 | skipped | 30.947 | skipped | skipped | 102.985 | 9.732 |
| lms__11 | 2.700 | 601.655 | skipped | 763.589 | skipped | skipped | 715.741 | 32.959 |
| lms__12 | 8.900 | error | skipped | timeout | skipped | skipped | timeout | timeout |
| lms__13 | 10.800 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| lms__14 | 28.000 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| lms__15 | 60.100 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| lms__16 | 152.700 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| mono__1000 | error | error | skipped | timeout | skipped | skipped | error | skipped |
| mono__2000 | skipped | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| mono__3000 | skipped | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| since__1000 | 24.631 | error | skipped | 13.905 | skipped | skipped | 60.517 | skipped |
| since__2000 | 125.914 | skipped | skipped | 38.315 | skipped | skipped | 421.150 | skipped |
| since__3000 | 395.262 | skipped | skipped | 157.213 | skipped | skipped | timeout | skipped |
| slb__1000 | 24.286 | error | skipped | 9.850 | skipped | skipped | 78.873 | skipped |
| slb__2000 | 163.625 | skipped | skipped | 40.681 | skipped | skipped | 540.596 | skipped |
| slb__3000 | 421.487 | skipped | skipped | 886.985 | skipped | skipped | timeout | skipped |
| eq__1 | 0.793 | 0.662 | 0.804 | 0.846 | 0.703 | skipped | 0.570 | skipped |
| eq__2 | 0.772 | 0.835 | 0.679 | 1.156 | 0.679 | skipped | 0.554 | skipped |
| eq__3 | 0.817 | 0.741 | 0.674 | 1.060 | 0.648 | skipped | 0.649 | skipped |
| eq__4 | 0.832 | 0.829 | 0.724 | 1.120 | 0.776 | skipped | 0.613 | skipped |
| eq__5 | 1.018 | 0.978 | 0.789 | 1.108 | 2.089 | skipped | 0.633 | skipped |
| eq__6 | 0.742 | 1.181 | 0.854 | 1.290 | 21.640 | skipped | 0.711 | skipped |
| eq__7 | 0.907 | 2.247 | 1.313 | 1.707 | 717.658 | skipped | 0.814 | skipped |
| eq__8 | 0.971 | 3.685 | 2.245 | 2.047 | timeout | skipped | 1.504 | skipped |
| eq__9 | 1.289 | 8.977 | 6.973 | 4.475 | skipped | skipped | 4.040 | skipped |
| eq__10 | 2.125 | 34.337 | 39.876 | 8.604 | skipped | skipped | 23.511 | skipped |
| eq__11 | 3.264 | 148.190 | 218.709 | 33.851 | skipped | skipped | 188.691 | skipped |
| eq__12 | 8.184 | 754.685 | timeout | timeout | skipped | skipped | timeout | skipped |
| eq__13 | 14.685 | error | skipped | skipped | skipped | skipped | skipped | skipped |
| eq__14 | 46.853 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| eq__15 | 144.512 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| eq__16 | 382.484 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
