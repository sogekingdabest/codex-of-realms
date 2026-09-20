package dev.codexofrealms.lore.infrastructure.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import dev.codexofrealms.content.EmbeddingDescriptor;
import dev.codexofrealms.lore.application.port.RetrievalQuery;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class HybridRetrievalIntegrationTest {
    @Container static final PostgreSQLContainer DB = new PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:0.8.6-pg18-trixie").asCompatibleSubstituteFor("postgres"));
    JdbcClient jdbc; PgVectorLoreRetriever retriever;
    UUID realm = UUID.randomUUID(), user = UUID.randomUUID(), member = UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        jdbc = JdbcClient.create(ds); retriever = new PgVectorLoreRetriever(jdbc);
        ReflectionTestUtils.setField(retriever, "hybridEnabled", true);
        try (var connection = ds.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                DROP SCHEMA public CASCADE; CREATE SCHEMA public; CREATE EXTENSION vector;
                CREATE TABLE realm(id uuid, active boolean);
                CREATE TABLE realm_membership(id uuid, realm_id uuid, user_id uuid, active boolean, role text);
                CREATE TABLE source_document(id uuid, realm_id uuid, active boolean, title text);
                CREATE TABLE access_policy(id uuid, realm_id uuid, active boolean, classification text);
                CREATE TABLE access_grant(realm_id uuid, policy_id uuid, membership_id uuid);
                CREATE TABLE document_version(id uuid, realm_id uuid, document_id uuid, active boolean,
                    processing_status text, embedding_provider text, embedding_model text, embedding_dimension int,
                    access_policy_id uuid, version_number int, original_filename text, checksum_sha256 text);
                CREATE TABLE lore_chunk(id uuid, realm_id uuid, document_version_id uuid, content text,
                    heading text, start_offset int, end_offset int, embedding vector(3));
                """);
        }
        jdbc.sql("INSERT INTO realm VALUES (:r,true)").param("r",realm).update();
        jdbc.sql("INSERT INTO realm_membership VALUES (:m,:r,:u,true,'PLAYER')")
            .param("m",member).param("r",realm).param("u",user).update();
    }
    UUID source(String text, String classification, String vector) {
        UUID id=UUID.randomUUID();
        jdbc.sql("INSERT INTO source_document VALUES (:id,:r,true,'Source')").param("id",id).param("r",realm).update();
        jdbc.sql("INSERT INTO access_policy VALUES (:id,:r,true,:c)").param("id",id).param("r",realm).param("c",classification).update();
        jdbc.sql("INSERT INTO document_version VALUES (:id,:r,:id,true,'READY','test','v1',3,:id,1,'source.md','hash')")
            .param("id",id).param("r",realm).update();
        jdbc.sql("INSERT INTO lore_chunk VALUES (:id,:r,:id,:text,NULL,0,:end,CAST(:v AS vector))")
            .param("id",id).param("r",realm).param("text",text).param("end",text.length()).param("v",vector).update();
        return id;
    }
    java.util.List<dev.codexofrealms.lore.RetrievedEvidence> retrieve(String question) {
        return retriever.retrieve(new RetrievalQuery(realm,user,new float[]{1,0,0},new EmbeddingDescriptor("test","v1"),10,question));
    }
    @Test void fusesOutsideVectorTopTenWithoutChangingCosineAndDeduplicates() {
        for(int i=0;i<12;i++) source("Piedra sin relación", "PUBLIC", "[1,0,0]");
        UUID lexical=source("Los mapas oficiales y las marcas antiguas no concuerdan.","PUBLIC","[0,1,0]");
        var result=retrieve("mapas oficiales marcas antiguas");
        assertThat(result).hasSize(10);
        assertThat(result.stream().map(p->p.chunkId()).distinct()).hasSize(10);
        var match=result.stream().filter(p->p.chunkId().equals(lexical)).findFirst().orElseThrow();
        assertThat(match.similarity()).isZero();
        assertThat(match.retrievalSignals().vectorRank()).isZero();
        assertThat(match.retrievalSignals().lexicalRank()).isEqualTo(1);
        assertThat(match.retrievalSignals().fusionScore()).isCloseTo(1.0/61,org.assertj.core.data.Offset.offset(.000001));
        assertThat(match.retrievalSignals().sufficient(1)).isTrue();
        assertThat(retrieve("mapas oficiales marcas antiguas")).isEqualTo(result);
    }
    @Test void sameAuthorizationAppliesToLexicalPrivateRevealedRevokedAndInactiveVersions() {
        UUID publicId=source("mapas oficiales", "PUBLIC", "[0,1,0]");
        UUID privateId=source("mapas oficiales", "GM_ONLY", "[0,1,0]");
        UUID spoiler=source("mapas oficiales", "SPOILER", "[0,1,0]");
        assertThat(retrieve("mapas oficiales")).extracting(p->p.chunkId()).containsExactly(publicId);
        jdbc.sql("INSERT INTO access_grant VALUES (:r,:p,:m)").param("r",realm).param("p",spoiler).param("m",member).update();
        assertThat(retrieve("mapas oficiales")).extracting(p->p.chunkId()).containsExactlyInAnyOrder(publicId,spoiler);
        jdbc.sql("DELETE FROM access_grant").update();
        jdbc.sql("UPDATE document_version SET active=false WHERE id=:id").param("id",publicId).update();
        assertThat(retrieve("mapas oficiales")).isEmpty();
        jdbc.sql("UPDATE realm_membership SET role='OWNER'").update();
        assertThat(retrieve("mapas oficiales")).extracting(p->p.chunkId()).containsExactlyInAnyOrder(privateId,spoiler);
        jdbc.sql("UPDATE realm_membership SET active=false").update();
        assertThat(retrieve("mapas oficiales")).isEmpty();
    }
    @Test void emptyTermsUseOnlyVectorAndSingleTermsCannotBypassGate() {
        source("el mapa", "PUBLIC", "[0,1,0]");
        assertThat(retrieve("el de la").getFirst().retrievalSignals().lexicalRank()).isZero();
        assertThat(retrieve("mapa").getFirst().retrievalSignals().sufficient(.8)).isFalse();
    }
}
