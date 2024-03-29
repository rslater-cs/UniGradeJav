package com.rysl.unigradejav.src.Database;

import android.database.Cursor;

import com.rysl.unigradejav.src.learningTree.Assignment;
import com.rysl.unigradejav.src.learningTree.Learner;
import com.rysl.unigradejav.src.learningTree.Module;
import com.rysl.unigradejav.src.learningTree.Subject;

import java.util.ArrayList;

public class sqlAccess_final {
    private DatabaseHelper database;

    public sqlAccess_final(DatabaseHelper database){
        this.database = database;
    }

    //----------------------------------------------------------------------------------------------

    public ArrayList<Learner> getLearner(Learner parent, String table){
        database.reopen();
        String query = "SELECT "+table+".* FROM ";
        if(parent != null){
            query += parent.getTable()+" JOIN "+parent.getSubTable()+" ON "+parent.getTable()+".ID="
                    +parent.getSubTable()+"."+parent.getSubID()[0]+" JOIN "+table+" ON "
                    +parent.getSubTable()+"."+parent.getSubID()[1]+"="+table
                    +".ID WHERE "+parent.getTable()+".ID="+parent.getKey();
        } else{
            query += table;
        }

        Cursor results = database.getData(query);

        ArrayList<Learner> learners = new ArrayList<>();

        if(table == "subject"){
            while(results.moveToNext()){
                learners.add(new Subject(results.getInt(0),
                        results.getString(1)));
            }
        } else if(table == "module"){
            while(results.moveToNext()){
                learners.add(new Module(results.getInt(0),
                        results.getString(1), parent));
            }
        } else if(table == "assignment"){
            while(results.moveToNext()){
                learners.add(new Assignment(results.getInt(0),
                        results.getString(1), results.getInt(2),
                        (results.getInt(3) > 0), parent));
            }
        }
        database.close();
        return learners;
    }

    public int getResult(Learner learner){
        database.reopen();
        String query = "SELECT RESULT FROM assignment WHERE ID = " + learner.getKey() + ";";
        Cursor results = database.getData(query);
        results.moveToNext();
        database.close();
        return results.getInt(0);
    }

    public String getDescription(Learner learner){
        database.reopen();
        String query = "SELECT DESCRIPTION FROM assignment WHERE ID = " + learner.getKey() + ";";
        Cursor results = database.getData(query);
        results.moveToNext();
        database.close();
        return results.getString(0);
    }

    public ArrayList<Double> getWorkingPercent(Learner learner, ArrayList<Double> percentages){
        String table = "end";
        switch (learner.getTable()){
            case "subject": table = "module";
            break;

            case "module": table = "assignment";
            break;
        }
        if(table != "end"){
            ArrayList<Learner> learners = this.getLearner(learner, table);
            for(int x = 0; x<learners.size(); x++){
                System.out.println(x);
                percentages = getWorkingPercent(learners.get(x), percentages);
            }
            System.out.println(percentages);
            return percentages;
        }
        database.reopen();
        Cursor result = database.getData("SELECT RESULT FROM assignment WHERE ID = " +
                learner.getKey() + ";");
        result.moveToNext();
        database.close();
        double percent = result.getInt(0);
        double percentage = learner.getPercentage();
        if(percent != -1.0){
            percentages.add(percent);
        } else{
            percentages.add(0.0);
        }
        percentages.add(percentage);
        return percentages;
    }

    public double getPredictedPercentage(Learner learner){
        ArrayList<Double> percentages = getWorkingPercent(learner, new ArrayList<Double>());
        int x = 0;
        double totalPercent = 0.0;
        double totalWeight = 0.0;
        while(x < (percentages.size())){
            System.out.println(x);
            if(percentages.get(x) == 0.0){
                percentages.remove(x);
                percentages.remove(x);
            } else{
                totalPercent += percentages.get(x) * (percentages.get(x+1)/100);
                totalWeight += percentages.get(x+1);
                x+=2;
            }
        }
        System.out.println("end");
        System.out.println(totalPercent);
        System.out.println(totalWeight);
        return (double) Math.round(((totalPercent*100) / totalWeight)*10)/10;
    }

    //----------------------------------------------------------------------------------------------

    public void setLearner(Learner parent, String name, int percentage, Boolean type){
        database.reopen();
        String query = "INSERT INTO ";
        String subQuery;

        String table = "subject";
        if(parent != null){
            table = "module";
            if(parent.getSubID()[0] == "modID"){
                table = "assignment";
            }
        }

        int PK = database.getMaxKey("SELECT MAX(id) FROM " + table);

        query += table + " VALUES (" + (PK+1) + ", " + "\"" + name + "\"";
        if(table != "subject"){
            int subPK = database.getMaxKey("SELECT MAX("
                    + parent.getSubID()[1] + ") FROM " + parent.getSubTable());
            subQuery = "INSERT INTO " + parent.getSubTable() + " VALUES(" + parent.getKey()
                    + ", " + (subPK+1) + ");";
            database.insertData(subQuery);
            if(type != null){
                query += ", " + percentage + ", " + convertType(type) + ", -1, null";
            }
        }
        query += ");";
        database.insertData(query);
        database.close();
    }

    public void setDescription(Learner learner, String description){
        database.reopen();
        String query = "UPDATE assignment SET DESCRIPTION = \"" + description + "\" WHERE ID = "
                + learner.getKey() + ";";
        database.insertData(query);
        database.close();
    }

    private int convertType(boolean type){
        if(type){
            return 1;
        }
        return 0;
    }

    public void setResult(Learner learner, int result){
        database.reopen();
        String query = "UPDATE assignment SET RESULT = " + result + " WHERE ID = "
                + learner.getKey() + ";";
        database.insertData(query);
        database.close();
    }

    //----------------------------------------------------------------------------------------------

    public void deleteLearner(int key, String table){
        database.reopen();
        if(table == "assignment"){
            database.deleteData("DELETE FROM link_mod_asg WHERE asgID=" + key);
        } else if(table == "module"){
            database.deleteData("DELETE FROM link_sub_mod WHERE modID = " + key);
        }
        deleteDownBranch(key, table);
        database.close();
    }

    private void deleteDownBranch(int key, String table){
        String query = "DELETE FROM " + table + " WHERE ID = " + key;
        database.deleteData(query);
        if(table != "assignment"){
            String[] IDs = new String[2];
            String subTable = "";

            if(table == "subject"){
                IDs = new String[]{"modID", "subID"};
                subTable = "link_sub_mod";
                table = "module";
            } else if(table == "module"){
                IDs = new String[]{"asgID", "modID"};
                subTable = "link_mod_asg";
                table = "assignment";
            }

            ArrayList<Integer> keys = database.getKeys("SELECT " + IDs[0] + " FROM "
                    + subTable + " WHERE " + IDs[1] +" = " + key);

            for(int x = 0; x<keys.size(); x++){
                deleteDownBranch(keys.get(x), table);
            }

            database.deleteData("DELETE FROM " + subTable + " WHERE " + IDs[1] + " = " + key);
        }
    }

    //----------------------------------------------------------------------------------------------

    public void editLearner(Learner learner, String name, int percent, Boolean type){
        database.reopen();
        String query = "UPDATE " + learner.getTable() + " SET NAME = \"" + name + "\"";
        if(learner.getTable() == "assignment"){
            query += ", PERCENTAGE = " + percent + ", TYPE = " + convertType(type);
        }
        query += " WHERE ID = " + learner.getKey() + ";";
        database.insertData(query);
        database.close();
    }

}
