#!/bin/sh
function usage {
	echo
	echo "Script to create/link/index a graph database. "
	echo
	echo "Usage: $1 -d <db_name> -m <mode>"
	echo
	echo "-d <db_name>	the graph database name "
	echo "-m <mode>		create/index/unindex tables "
	echo
} 

#
#Set default value for variables
#
graphDb=""
mode=""
h="white"

while getopts d:m: opt
do
  case "$opt" in
  	d) graphDb="$OPTARG";;
  	m) mode="$OPTARG";;
  esac
done

if [ -z "$graphDb" ] || [ -z "$mode" ]
then
	echo "Missing or more arguments"
	usage $0
	exit 1
fi

arch=`uname -m`
case "$arch" in
    i686)
	mysqldir=/project/tla/dist/mysql-i686
    ;;
    x86_64)
	mysqldir=/project/tla/dist/mysql
    ;;
    *)
	mysqldir=/project/tla/dist/mysql-i686
    ;;
esac

mysqlbin=$mysqldir/bin/mysql
master=white
db=test

if [ "$mode" == "CREATE" ]
then
$mysqlbin -pnieve -h $h -B -N $db <<ENDSQL
	SET sql_mode = "NO_UNSIGNED_SUBTRACTION,TRADITIONAL";
	CREATE DATABASE IF NOT EXISTS ${graphDb}
		DEFAULT CHARACTER SET latin1 DEFAULT COLLATE latin1_general_cs;
ENDSQL
$mysqlbin -pnieve -h $h -B -N $graphDb <<ENDSQL1
	SET sql_mode = "NO_UNSIGNED_SUBTRACTION,TRADITIONAL";
	CREATE TABLE chain_graph LIKE abstract.chain_graph;
	CREATE TABLE single_model_graph LIKE abstract.single_model_graph;
	CREATE TABLE single_model_node LIKE abstract.single_model_node;
	CREATE TABLE single_model_edge LIKE abstract.single_model_edge;
	CREATE TABLE pdb_residue_info LIKE abstract.pdb_residue_info;
	
	ALTER TABLE chain_graph
		MODIFY graph_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
	  	MODIFY scops INT,
	  	MODIFY caths INT,
	  	MODIFY entry_id INT,
	  	MODIFY assembly_id INT,
	  	MODIFY chain_id INT,
	  	MODIFY model_id INT;
	
	ALTER TABLE single_model_graph
		MODIFY graph_id INT UNSIGNED NOT NULL AUTO_INCREMENT;
	
	ALTER TABLE single_model_node
		MODIFY ssid VARCHAR(5),
		MODIFY sheet_serial CHAR(1);
		
	ALTER TABLE single_model_edge
		MODIFY i_ssid VARCHAR(5),
		MODIFY i_sheet_serial CHAR(1),
		MODIFY j_ssid VARCHAR(5),
		MODIFY j_sheet_serial CHAR(1);		
ENDSQL1
fi

if [ "$mode" == "INDEX" ]
then
$mysqlbin -pnieve -h $h -B -N $graphDb <<ENDSQL
	SET sql_mode = "NO_UNSIGNED_SUBTRACTION,TRADITIONAL";	
	CREATE INDEX NODE_GRAPH_IDX ON single_model_node (graph_id);
	CREATE INDEX EDGE_GRAPH_IDX ON single_model_edge (graph_id);	
ENDSQL
fi

if [ "$mode" == "UNINDEX" ]
then
$mysqlbin -pnieve -h $h -B -N $graphDb <<ENDSQL
	SET sql_mode = "NO_UNSIGNED_SUBTRACTION,TRADITIONAL";	
	DROP INDEX NODE_GRAPH_IDX ON single_model_node;
	DROP INDEX EDGE_GRAPH_IDX ON single_model_edge;	
ENDSQL
fi
