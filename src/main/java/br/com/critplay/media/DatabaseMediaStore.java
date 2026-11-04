package br.com.critplay.media;

import br.com.critplay.shared.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Small hobby deployments keep sanitized uploads with the relational data. */
@Service
public class DatabaseMediaStore {
    private final JdbcTemplate db;
    private final DatabaseLock lock;
    private final long budget;
    public DatabaseMediaStore(JdbcTemplate db,DatabaseLock lock,
            @Value("${app.media.max-total-bytes:134217728}") long budget) {
        this.db=db;this.lock=lock;this.budget=budget;
    }
    @Transactional(rollbackFor=IOException.class)
    public void save(Long owner,Path image,Path poster) throws IOException {
        lock.acquire("media");
        byte[] content=Files.readAllBytes(image);
        byte[] preview=poster==null?null:Files.readAllBytes(poster);
        long used=db.queryForObject("SELECT COALESCE(SUM(byte_size),0) FROM media_files",Long.class);
        if(used+content.length+(preview==null?0:preview.length)>budget)
            throw new ServiceUnavailableException("Armazenamento de imagens cheio. Contate a administração.");
        insert(owner,image.getFileName().toString(),content);
        if(preview!=null)insert(owner,poster.getFileName().toString(),preview);
    }
    private void insert(Long owner,String name,byte[] content) {
        db.update("INSERT INTO media_files(filename,owner_id,content,byte_size,created_at) VALUES (?,?,?,?,?)",
                name,owner,content,content.length,java.time.OffsetDateTime.now());
    }
    public boolean owned(Long owner,String name) {
        return db.queryForObject("SELECT COUNT(*) FROM media_files WHERE filename=? AND owner_id=?",Long.class,name,owner)>0;
    }
    public Optional<byte[]> read(String name) {
        return db.query("SELECT content FROM media_files WHERE filename=?",(rs,n)->rs.getBytes(1),name).stream().findFirst();
    }
    private List<Map<String,Object>> candidates() {
        return db.queryForList("""
            SELECT filename,byte_size FROM media_files m WHERE created_at<?
            AND NOT EXISTS (SELECT 1 FROM app_users u WHERE u.avatar_url=CONCAT('/media/',m.filename) OR u.banner_url=CONCAT('/media/',m.filename)
                OR REPLACE(u.avatar_url,'.gif','.png')=CONCAT('/media/',m.filename) OR REPLACE(u.banner_url,'.gif','.png')=CONCAT('/media/',m.filename))
            AND NOT EXISTS (SELECT 1 FROM games g WHERE g.cover_url=CONCAT('/media/',m.filename)
                OR REPLACE(g.cover_url,'.gif','.png')=CONCAT('/media/',m.filename))
            ORDER BY filename LIMIT 1000
            """,java.time.OffsetDateTime.now().minusHours(24));
    }
    @Transactional public Map<String,Object> preview() {
        lock.acquire("media");var files=candidates();
        return Map.of("count",files.size(),"bytes",files.stream().mapToLong(f->((Number)f.get("byte_size")).longValue()).sum(),"graceHours",24,"limit",1000);
    }
    @Transactional public Map<String,Object> clean() {
        lock.acquire("media");int count=0;
        for(var file:candidates())count+=db.update("DELETE FROM media_files WHERE filename=?",file.get("filename"));
        return Map.of("removed",count,"failed",0,"graceHours",24);
    }
}
