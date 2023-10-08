LOCAL_REPO="/home/gtroitsk/Downloads/RHBQ/rh-quarkus-platform-3.2.3.ER3-maven-repository/rh-quarkus-platform-3.2.3.GA-maven-repository/maven-repository/"

QUARKUS_VERSION_NEW="3.2.3.ER3"
QUARKUS_VERSION_OLD="2.13.8.CR3"

rm -f artifacts_*

GA="com.aayushatharva.brotli4j:service"

GROUP_ID=`echo $GA | cut -d: -f1,1`
ARTIFACT_ID=`echo $GA | cut -d: -f2,2`

# List of POMs with added artifact
grep -l ">$ARTIFACT_ID<" $LOCAL_REPO -R | grep ".*.pom" > pom_list.txt

    while read pom; do
        DEPENDENTS=`xmllint --xpath '/*[local-name()="project"]/*[local-name()="dependencies"]/*[local-name()="dependency"]' "$pom" 2>/dev/null | grep -A2 ">$GROUP_ID<" | grep -A2 ">$ARTIFACT_ID<" || true `

        if [[ -n "$DEPENDENTS" ]];
        then
          VERSION=`echo "$DEPENDENTS" | grep -Pzo '(?s)<version>.*</version>' | tr '\0' '\n' | sed 's/<version>//g' | sed 's/<\/version>//g'`
          # If "parent" substring is in the path -> all data are in project namespace
          # Overwise extract groupId and version from parent namespace

          DEPENDENT_ARTIFACTID=`xmllint --xpath '/*[local-name()="project"]/*[local-name()="artifactId"]/text()' "$pom" 2>/dev/null || true`
          DEPENDENT_GROUPID=`xmllint --xpath '/*[local-name()="project"]/*[local-name()="parent"]/*[local-name()="groupId"]/text()' "$pom" 2>/dev/null || true`
          DEPENDENT_VERSION=`xmllint --xpath '/*[local-name()="project"]/*[local-name()="version"]/text()' "$pom" 2>/dev/null || true`

          if [[ -z "$DEPENDENT_GROUPID" ]];
          then
            DEPENDENT_GROUPID=`xmllint --xpath '/*[local-name()="project"]/*[local-name()="groupId"]/text()' "$pom"`
          fi

          if [[ -z "$DEPENDENT_VERSION" ]];
          then
            DEPENDENT_VERSION=`xmllint --xpath '/*[local-name()="project"]/*[local-name()="parent"]/*[local-name()="version"]/text()' "$pom"`
          fi

          echo "${DEPENDENT_GROUPID}:${DEPENDENT_ARTIFACTID}:${DEPENDENT_VERSION} <- ${GROUP_ID}:${ARTIFACT_ID}:${VERSION}" >> artifacts_${QUARKUS_VERSION_NEW}_ADDED.txt
        fi
    done < pom_list.txt
    echo "" >> artifacts_${QUARKUS_VERSION_NEW}_ADDED.txt
