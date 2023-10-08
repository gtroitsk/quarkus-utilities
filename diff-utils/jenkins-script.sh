                       
rm -f artifacts_*

QUARKUS_VERSION_NEW="3.2.3.ER3"
QUARKUS_VERSION_OLD="2.13.8.CR3"

wget -O artifacts_${QUARKUS_VERSION_NEW}.txt http://download.eng.bos.redhat.com/rcm-guest/staging/quarkus/quarkus-platform-${QUARKUS_VERSION_NEW}/extras/repository-artifact-list.txt
wget -O artifacts_${QUARKUS_VERSION_OLD}.txt  http://download.eng.bos.redhat.com/rcm-guest/staging/quarkus/quarkus-platform-${QUARKUS_VERSION_OLD}/extras/repository-artifact-list.txt

cat artifacts_${QUARKUS_VERSION_NEW}.txt | cut -d: -f1,2 | tee artifacts_${QUARKUS_VERSION_NEW}_GA.txt
cat artifacts_${QUARKUS_VERSION_OLD}.txt | cut -d: -f1,2 | tee artifacts_${QUARKUS_VERSION_OLD}_GA.txt

echo 'get & unzip productized maven repo'
wget -q -O quarkus-maven-repo.zip "http://download.eng.bos.redhat.com/rcm-guest/staging/quarkus/quarkus-platform-${QUARKUS_VERSION_NEW}/rh-quarkus-platform-${QUARKUS_VERSION_NEW}-maven-repository.zip"
MAVEN_REPO_ROOT_DIR_NAME=$(unzip -Z -1 quarkus-maven-repo.zip | head -n 1 | cut -d '/' -f 1)
unzip -q quarkus-maven-repo.zip

wget -O ~/.m2/settings.xml https://gitlab.cee.redhat.com/quarkus-qe/jenkins-jobs/-/raw/main/jobs/rhbq/files/settings.xml
sed -i -e "s|/path_to_repo|$PWD/${MAVEN_REPO_ROOT_DIR_NAME}/maven-repository|" ~/.m2/settings.xml
LOCAL_REPO="$(pwd)/${MAVEN_REPO_ROOT_DIR_NAME}/maven-repository/"

while read line; do
  NEW_VERSION=`echo $line | cut -d: -f3`
  GA=`echo $line | cut -d: -f1-2`
  OLD_VERSION_COUNT=`cat artifacts_${QUARKUS_VERSION_OLD}.txt | grep "$GA:" | cut -d: -f3 | wc -w`

  if [ $OLD_VERSION_COUNT -eq 0 ]; then
    echo "$GA" >> artifacts_${QUARKUS_VERSION_NEW}_ADDED.txt
  fi
done < artifacts_${QUARKUS_VERSION_NEW}.txt

wc -l artifacts_*.txt
        