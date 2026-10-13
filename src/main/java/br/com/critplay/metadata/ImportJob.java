package br.com.critplay.metadata;
public record ImportJob(long id,String status,int target,int processed,int added,int existing,int skipped,int page,int index,String message,long owner){}
