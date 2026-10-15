package br.com.critplay.migration;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Ferramenta local. Não é exposta pela API e não inicia o servidor. */
public final class H2ToPostgres {
    private static final List<String> TABLES=List.of("games","app_users","diary_entries","game_lists","game_list_items","user_follows","friendships","user_blocks","content_reports","account_audit","import_jobs","profile_featured_games","review_likes","activity_events");
    private static final Set<String> IDENTITIES=Set.of("games","app_users","diary_entries","game_lists","game_list_items","content_reports","account_audit","import_jobs","activity_events");
    private H2ToPostgres(){}
    public static Map<String,Long> migrate(Connection source,Connection target) throws SQLException {
        if(!source.getMetaData().getDatabaseProductName().equals("H2")||!target.getMetaData().getDatabaseProductName().equals("PostgreSQL"))throw new SQLException("Bancos incompatíveis.");
        target.setAutoCommit(false);var totals=new LinkedHashMap<String,Long>();
        try {
            // Trava todo o destino até o commit. Não aceita uma base já utilizada.
            try(var statement=target.createStatement()){statement.execute("LOCK TABLE "+String.join(",",TABLES)+" IN ACCESS EXCLUSIVE MODE");}
            for(String table:TABLES){try(var statement=target.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)){rows.next();if(rows.getLong(1)!=0)throw new SQLException("O destino precisa estar vazio.");}}
            for(String table:TABLES){
                try(var read=source.createStatement();var rows=read.executeQuery("SELECT * FROM "+table)){
                    var meta=rows.getMetaData();var columns=new ArrayList<String>();for(int i=1;i<=meta.getColumnCount();i++)columns.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
                    try(var statement=target.createStatement();var empty=statement.executeQuery("SELECT * FROM "+table+" WHERE 1=0")){
                        var expected=new HashSet<String>();for(int i=1;i<=empty.getMetaData().getColumnCount();i++)expected.add(empty.getMetaData().getColumnName(i));
                        if(!expected.equals(new HashSet<>(columns)))throw new SQLException("Atualize a versão do banco de origem antes da migração.");
                    }
                    String sql="INSERT INTO "+table+"("+String.join(",",columns)+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";long count=0;
                    try(var insert=target.prepareStatement(sql)){
                        while(rows.next()){
                            for(int i=1;i<=columns.size();i++){
                                Object value=rows.getObject(i);
                                if(table.equals("app_users")&&columns.get(i-1).equals("session_version"))value=((Number)value).intValue()+1;
                                if(table.equals("import_jobs")&&columns.get(i-1).equals("status")&&"RUNNING".equals(value))value="PAUSED";
                                insert.setObject(i,value);
                            }
                            insert.addBatch();count++;if(count%250==0){insert.executeBatch();insert.clearBatch();}
                        }insert.executeBatch();
                    }
                    totals.put(table,count);
                }
            }
            // Garante que cadastros novos não reutilizem IDs importados.
            for(String table:IDENTITIES){try(var statement=target.createStatement();var rows=statement.executeQuery("SELECT COALESCE(MAX(id),0) FROM "+table)){
                rows.next();long next=Math.max(table.equals("games")?1000:1,rows.getLong(1)+1);
                try(var alter=target.createStatement()){alter.execute("ALTER TABLE "+table+" ALTER COLUMN id RESTART WITH "+next);}
            }}
            for(String table:TABLES){try(var statement=target.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)){rows.next();if(rows.getLong(1)!=totals.get(table))throw new SQLException("Contagem divergente.");}}
            target.commit();return totals;
        }catch(SQLException|RuntimeException failure){target.rollback();throw failure;}finally{target.setAutoCommit(true);}
    }
    public static void run(){
        var properties=new Properties();try{
            if(Files.exists(Path.of(".env")))try(var reader=Files.newBufferedReader(Path.of(".env"))){properties.load(reader);}
            String url=value(properties,"DB_URL",""),user=value(properties,"DB_USERNAME",""),password=value(properties,"DB_PASSWORD","");
            if(!url.startsWith("jdbc:postgresql:")||url.toLowerCase(Locale.ROOT).contains("password=")||url.toLowerCase(Locale.ROOT).contains("user=")||user.isBlank())throw new IllegalArgumentException("Configure DB_URL, DB_USERNAME e DB_PASSWORD para o destino.");
            String sourceUrl=value(properties,"H2_SOURCE_URL","jdbc:h2:file:./data/gameshelf");
            if(!sourceUrl.startsWith("jdbc:h2:file:")||sourceUrl.contains(";"))throw new IllegalArgumentException("Informe apenas uma URL jdbc:h2:file para a origem.");
            Flyway.configure().dataSource(url,user,password).locations("classpath:db/postgresql").load().migrate();
            try(var source=DriverManager.getConnection(sourceUrl+";IFEXISTS=TRUE;ACCESS_MODE_DATA=r",value(properties,"H2_SOURCE_USERNAME","sa"),value(properties,"H2_SOURCE_PASSWORD",""));var target=DriverManager.getConnection(url,user,password)){
                var counts=migrate(source,target);counts.forEach((table,count)->System.out.println(table+": "+count));
            }
            System.out.println("Migração concluída. Copie também data/uploads para o armazenamento persistente do servidor.");
        }catch(Exception failure){System.err.println("Migração não concluída ("+failure.getClass().getSimpleName()+"). Confira origem atualizada/parada, destino vazio e credenciais. Nenhuma linha de negócio foi parcialmente gravada.");System.exit(1);}
    }
    private static String value(Properties properties,String key,String fallback){return System.getenv().getOrDefault(key,properties.getProperty(key,fallback));}
}
