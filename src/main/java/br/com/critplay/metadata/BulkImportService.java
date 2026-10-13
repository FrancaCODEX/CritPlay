package br.com.critplay.metadata;
import br.com.critplay.users.UserService;
import br.com.critplay.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import java.util.*;
import java.util.concurrent.*;

@Service @Order(30)
public class BulkImportService implements CommandLineRunner,DisposableBean {
    private final br.com.critplay.steam.SteamResolver steam;private final RawgClient client;private final ImportJobStore store;private final UserService users;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var thread=new Thread(r,"critplay-import");thread.setDaemon(true);return thread;});
    private final Set<Long> running=ConcurrentHashMap.newKeySet();
    public BulkImportService(RawgClient client,ImportJobStore store,UserService users,br.com.critplay.steam.SteamResolver steam){this.steam=steam;this.client=client;this.store=store;this.users=users;}
    public void run(String... args){store.recover();}
    public ImportJob create(String username,int count){if(!client.configured())throw new ServiceUnavailableException("Configure RAWG_API_KEY antes de iniciar a importação.");var job=store.create(users.require(username).getId(),count);launch(job.id());return job;}
    public ImportJob resume(String username,long id){if(!client.configured())throw new ServiceUnavailableException("Configure RAWG_API_KEY antes de retomar.");var job=store.resume(id,users.require(username).getId());launch(id);return job;}
    private void launch(long id){worker.submit(()->process(id));}
    private void process(long id){
        int pageNumber=-1;List<MetadataGame> page=List.of();
        try{while(!Thread.currentThread().isInterrupted()){
            var job=store.get(id);if(!job.status().equals("RUNNING"))return;
            if(!store.ownerActive(job.owner())){store.pause(id,"Administrador responsável sem acesso ativo.");return;}
            if(job.page()!=pageNumber){page=client.page(job.page());pageNumber=job.page();Thread.sleep(350);}
            if(page.isEmpty()){store.finish(id,"Fim do catálogo disponível na fonte.");return;}
            if(job.index()>=page.size()){store.pause(id,"Página da fonte mudou; revise a importação.");return;}
            MetadataGame metadata;try{metadata=client.detail(page.get(job.index()).id());}catch(NotFoundException missing){metadata=null;}var resolved=metadata!=null&&metadata.platforms().contains(br.com.critplay.catalog.Platform.PC)?steam.resolve(metadata.title()):null;
            store.consume(job,metadata,page.size(),resolved==null?null:resolved.appId());Thread.sleep(350);
        }}catch(InterruptedException e){Thread.currentThread().interrupt();store.pause(id,"Importação interrompida. Retome quando desejar.");}
        catch(ServiceUnavailableException e){store.pause(id,e.getMessage());}
        catch(RuntimeException e){store.pause(id,"A importação encontrou um erro. Os jogos já salvos foram preservados; revise e retome.");}
    }
    public void destroy(){worker.shutdownNow();try{worker.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
