# Figure 3: all baseline comparison

77 instances; 900 s end-to-end per repetition; 1 repetition(s) requested for remaining cases. Completed measurements are retained; each timing is the median of its recorded samples.
NuSMV used up to 2 concurrent engine workers. The LTL-circuit route uses one repetition and ABC PDR. Metadata records protocol changes. Larger family members after a timeout or size limit are explicitly skipped.

| Engine | Decided | Timeout | Size limit | Bound limit | Skipped | Other/pending |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct + ABC | 77 | 0 | 0 | 0 | 0 | 0 |
| Aalta | 43 | 17 | 13 | 0 | 4 | 0 |
| Lisa | 39 | 5 | 6 | 0 | 27 | 0 |
| Elimination + ABC | 57 | 3 | 2 | 0 | 15 | 0 |
| Elimination + NuSMV | 39 | 8 | 1 | 0 | 29 | 0 |
| LTL + NuSMV SAT-BMC | 34 | 7 | 2 | 0 | 34 | 0 |
| Direct + NuSMV | 66 | 6 | 0 | 0 | 5 | 0 |
| Circuit + NuSMV $k$-induction | 57 | 5 | 0 | 0 | 15 | 0 |

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
| no2a__24 | 0.500 | 0.600 | size_limit | 0.957 | 0.979 | 0.705 | 0.441 | 0.585 |
| no2a__200 | 0.500 | 0.600 | timeout | 1.126 | 1.369 | 0.838 | 0.562 | 0.638 |
| no2a__600 | 0.500 | 5.300 | timeout | 1.405 | 6.477 | 1.023 | 0.990 | 0.830 |
| no2a__1000 | 0.700 | 54.200 | skipped | 1.967 | 17.225 | 1.373 | 1.914 | 0.852 |
| no2a__4000 | 0.700 | timeout | skipped | 6.003 | 393.560 | 4.927 | 39.706 | 1.431 |
| no2a__64000 | 4.300 | timeout | skipped | timeout | timeout | timeout | timeout | 32.750 |
| mono__8 | 0.500 | 1.100 | 0.700 | 2.702 | 1.731 | timeout | 0.883 | 0.505 |
| mono__9 | 0.500 | 2.100 | 0.900 | 3.511 | 4.406 | skipped | 0.499 | 0.521 |
| mono__10 | 0.600 | 4.600 | 1.500 | 7.274 | 9.632 | skipped | 0.554 | 0.537 |
| mono__11 | 0.500 | 8.800 | 2.600 | 24.282 | 33.655 | skipped | 0.617 | 0.509 |
| mono__12 | 0.500 | 26.000 | 5.200 | 109.100 | 80.607 | skipped | 0.480 | 0.533 |
| mono__16 | 0.500 | size_limit | size_limit | size_limit | size_limit | skipped | 0.499 | 0.623 |
| mono__32 | 0.700 | size_limit | skipped | skipped | skipped | skipped | 0.672 | 0.876 |
| mono__128 | 3.900 | size_limit | skipped | skipped | skipped | skipped | 3.896 | 18.816 |
| mono__256 | 29.100 | size_limit | skipped | skipped | skipped | skipped | 23.108 | 193.077 |
| since__2 | 0.500 | 0.500 | 0.400 | 1.013 | 0.433 | 1.225 | 0.480 | 0.440 |
| since__4 | 0.900 | 0.500 | 0.500 | 2.234 | 0.464 | 0.703 | 0.614 | 0.479 |
| since__6 | 1.000 | 0.800 | 0.600 | 1.867 | 1.525 | 0.908 | 0.477 | 0.512 |
| since__8 | 1.300 | 2.000 | 1.300 | 2.435 | 213.155 | 1.452 | 0.459 | 0.515 |
| since__12 | 1.500 | 133.400 | 66.900 | timeout | timeout | 189.905 | 0.505 | 0.473 |
| since__16 | 1.500 | size_limit | size_limit | skipped | skipped | size_limit | 0.484 | 0.537 |
| since__32 | 1.300 | size_limit | skipped | skipped | skipped | skipped | 0.592 | 0.601 |
| since__128 | 1.600 | size_limit | skipped | skipped | skipped | skipped | 1.299 | 1.291 |
| since__256 | 2.200 | size_limit | skipped | skipped | skipped | skipped | 3.423 | 3.486 |
| slb__2 | 0.500 | 0.400 | 0.400 | 1.645 | 0.564 | 0.486 | 0.451 | 0.489 |
| slb__4 | 0.500 | 0.500 | 0.500 | 1.094 | 0.526 | 0.570 | 0.451 | 0.467 |
| slb__6 | 0.500 | 0.700 | 0.600 | 1.574 | 1.040 | 0.668 | 0.463 | 0.513 |
| slb__8 | 0.500 | 1.700 | 1.100 | 1.872 | 238.681 | 1.045 | 0.446 | 0.506 |
| slb__12 | 0.500 | 113.700 | 39.500 | 635.372 | timeout | 30.616 | 0.466 | 0.509 |
| slb__16 | 0.500 | size_limit | size_limit | size_limit | skipped | size_limit | 0.494 | 0.505 |
| slb__32 | 0.500 | size_limit | skipped | skipped | skipped | skipped | 0.566 | 0.649 |
| slb__128 | 1.000 | size_limit | skipped | skipped | skipped | skipped | 1.291 | 1.260 |
| slb__256 | 2.900 | size_limit | skipped | skipped | skipped | skipped | 3.589 | 3.289 |
| marks__200 | 0.600 | timeout | size_limit | 1.307 | 7.606 | timeout | 2.713 | 114.503 |
| marks__600 | 1.000 | timeout | size_limit | 3.088 | timeout | skipped | 56.102 | timeout |
| marks__800 | 0.800 | timeout | timeout | 5.387 | skipped | skipped | 306.465 | skipped |
| marks__1200 | 1.300 | timeout | skipped | 11.806 | skipped | skipped | timeout | skipped |
| marks__2400 | 2.000 | timeout | skipped | 45.489 | skipped | skipped | skipped | skipped |
| same__300 | 0.800 | timeout | 31.500 | 3.387 | 102.946 | timeout | 7.986 | timeout |
| same__400 | 0.700 | timeout | 81.300 | 3.382 | 437.309 | skipped | 16.724 | skipped |
| same__500 | 0.700 | timeout | 171.100 | 3.944 | timeout | skipped | 60.650 | skipped |
| same__600 | 0.900 | timeout | 349.500 | 4.810 | skipped | skipped | 123.968 | skipped |
| same__1200 | 1.100 | timeout | timeout | 14.658 | skipped | skipped | timeout | skipped |
| lms__1 | 0.500 | 0.400 | 1.000 | 0.859 | 1.335 | 1.226 | 0.785 | 0.931 |
| lms__2 | 0.500 | 0.500 | 474.900 | 0.881 | 0.774 | 1.152 | 0.593 | 0.510 |
| lms__3 | 0.500 | 0.500 | timeout | 0.924 | 0.868 | 1.666 | 0.564 | 0.521 |
| lms__4 | 0.500 | 0.500 | skipped | 1.213 | 1.123 | 1.959 | 0.989 | 0.539 |
| lms__5 | 0.500 | 0.700 | skipped | 1.235 | timeout | 3.839 | 0.534 | 0.688 |
| lms__6 | 0.600 | 1.200 | skipped | 1.401 | skipped | 10.330 | 0.626 | 0.770 |
| lms__7 | 0.700 | 2.600 | skipped | 2.247 | skipped | 57.316 | 1.048 | 1.158 |
| lms__8 | 0.800 | 5.100 | skipped | 3.602 | skipped | 372.187 | 2.649 | 2.152 |
| lms__9 | 1.100 | 11.300 | skipped | 9.609 | skipped | timeout | 16.816 | 4.005 |
| lms__10 | 1.700 | 58.500 | skipped | 41.111 | skipped | skipped | 102.985 | 9.732 |
| lms__11 | 2.700 | 422.700 | skipped | timeout | skipped | skipped | 715.741 | 32.959 |
| lms__12 | 8.900 | size_limit | skipped | skipped | skipped | skipped | timeout | timeout |
| lms__13 | 10.800 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| lms__14 | 28.000 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| lms__15 | 60.100 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
| lms__16 | 152.700 | skipped | skipped | skipped | skipped | skipped | skipped | skipped |
