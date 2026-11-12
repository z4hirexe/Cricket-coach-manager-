#!/bin/bash
# run one unit test class quickly: tools/run_dp_test.sh DeliveryPlanTest
cd /home/user/cricket-coach-manager
export JAVA_HOME=/home/user/.cache/dl/jdk17 PATH=/home/user/.cache/dl/jdk17/bin:$PATH
./gradlew --max-workers=1 testDebugUnitTest --tests "*${1:-DeliveryPlanTest}*" -Dorg.gradle.jvmargs=-Xmx640m -Pkotlin.compiler.execution.strategy=in-process --console=plain > /tmp/dp.log 2>&1; echo EXIT $? >> /tmp/dp.log
grep -E "^e: |EXIT|tests completed|BUILD" /tmp/dp.log | head -20
python3 - "$1" <<'PY'
import glob,re,sys
for f in glob.glob('/home/user/cricket-coach-manager/app/build/test-results/testDebugUnitTest/*%s*.xml'%(sys.argv[1] or 'DeliveryPlan')):
    s=open(f).read(); m=re.search(r'<system-out><!\[CDATA\[(.*?)\]\]>',s,re.S)
    print((m.group(1) if m else '')[:9000])
PY
