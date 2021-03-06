#!/bin/bash

echo -n Postgres Password:
read -s password

dbuser="mmsuser"
usedb="mms"

echo
while [[ -n "$1" ]]; do
    #statements
    case "$1" in
        -u | --user)
            shift
            case "$1" in
                -*)
                    echo "* * * * * * * * * * * "
                    echo ""
                    echo "No user name supplied"
                    echo "Using user $USER"
                    echo ""
                    echo "* * * * * * * * * * * "
                    dbuser=$USER
                    sleep 2
                    break;;
            esac

            if [[ -n "$1" ]]; then
                #statements
                dbuser="$1"
                echo "Starting server as user $dbuser"
                sleep 1
            fi
            ;;
        -db | --database)
            shift
            if [[ -n "$1" ]]; then
                #statements
                usedb="$1"
                echo "Using database : $usedb"
                sleep 1
            else
                echo "Using default database : $usedb"
                sleep 1
            fi
            ;;
        *)
            echo "* * * * * * * * * * * "
            echo ""
            echo "$1 is not an option"
            echo ""
            echo "* * * * * * * * * * * "
            sleep 4
            ;;
    esac
    shift
done


if [[ "$OSTYPE" == "darwin" ]]; then
    dropdb -U $dbuser _PA
    dropdb -U $dbuser _PB
    dropdb -U $dbuser _PC
    dropdb -U $dbuser _PD
    dropdb -U $dbuser _MountCommit
    dropdb -U $dbuser _CollaboratorProject
    dropdb -U $dbuser _CompleteModelGet
    dropdb -U $dbuser _MoveModel
    dropdb -U $dbuser $usedb
    createdb -U $dbuser $usedb
    psql -U $dbuser -f ./repo-amp/src/main/resources/mms.sql $usedb
else
    if [ ! -z $password ];then
        EXPECT=$(which expect)

        $EXPECT <<EOD
log_user 0
spawn dropdb -U $dbuser _PA
expect "*Password*"
send "$password\r"
expect eof
EOD

        $EXPECT <<EOD
log_user 0
spawn dropdb -U $dbuser _PB
expect "*Password*"
send "$password\r"
expect eof
EOD

        $EXPECT <<EOD
log_user 0
spawn dropdb -U $dbuser _PC
expect "*Password*"
send "$password\r"
expect eof
EOD

        $EXPECT <<EOD
log_user 0
spawn dropdb -U $dbuser _PD
expect "*Password*"
send "$password\r"
expect eof
EOD

        $EXPECT <<EOD
log_user 0
spawn dropdb -U $dbuser _MountCommit
expect "*Password*"
send "$password\r"
expect eof
EOD

        $EXPECT <<EOD
log_user 0
spawn dropdb -U $dbuser _CollaboratorProject
expect "*Password*"
send "$password\r"
expect eof
EOD

        $EXPECT <<EOD
log_user 0
spawn dropdb -U $dbuser _CompleteModelGet
expect "*Password*"
send "$password\r"
expect eof
EOD
        $EXPECT <<EOD
log_user 0
spawn dropdb -U $dbuser _MoveModel
expect "*Password*"
send "$password\r"
expect eof
EOD
        $EXPECT <<EOD
log_user 0
spawn dropdb -U $dbuser $usedb
expect "*Password*"
send "$password\r"
expect eof
EOD

        $EXPECT <<EOD
log_user 0
spawn createdb -U $dbuser $usedb
expect "*Password*"
send "$password\r"
expect eof
EOD

        $EXPECT <<EOD
log_user 0
spawn psql -U $dbuser -f ./repo-amp/src/main/resources/mms.sql $usedb
expect "*Password*"
send "$password\r"
expect eof
EOD

    else
        dropdb -U $dbuser _PA
        dropdb -U $dbuser _PB
        dropdb -U $dbuser _PC
        dropdb -U $dbuser _PD
        dropdb -U $dbuser _MountCommit
        dropdb -U $dbuser _CollaboratorProject
        dropdb -U $dbuser _CompleteModelGet
        dropdb -U $dbuser _MoveModel
        dropdb -U $dbuser $usedb
        createdb -U $dbuser $usedb
        psql -U $dbuser -f ./repo-amp/src/main/resources/mms.sql $usedb

        echo "Dropping previous databases"
    fi
fi

echo -n "deleting elastic db: "
curl -XDELETE 'http://localhost:9200/_all/'
sh ./repo-amp/src/main/resources/mms_mappings.sh
rm -rf ./alf_data_dev
./mvnw clean -Ddependency.surf.version=6.3 -Ppurge
