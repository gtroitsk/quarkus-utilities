FILE=added.txt

LOCAL_REPO="/home/gtroitsk/.m2/repository/"
NEW_VERSION="3.5.0"

POM_DIR_NAME=$LOCAL_REPO
POM_DIR_NAME+=`cat "$FILE" | cut -d ":" -f1,1 | sed 's/\./\//g'`
DIR_NAME=`cat "$FILE" | cut -d ":" -f2,2`
POM_DIR_NAME+="/$DIR_NAME/$NEW_VERSION"
POM_DIR_NAME+="/$DIR_NAME-$NEW_VERSION.pom"

# Removes unnecessary info from dependency:tree command output
mvn -f "$POM_DIR_NAME" dependency:tree | sed 's/\[INFO\] //g' | sed '1,/maven-dependency-plugin/d' | sed '/--/,$d'
