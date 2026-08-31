package dev.codexofrealms.content.application.port;

public interface RawSourceStorage {

    void write(String key, byte[] content);

    byte[] read(String key);

    void delete(String key);
}
