# Dependências — auditoria de 12/10/2026

Consulta OSV em 2026-10-12T21:22:34.264Z. Foram examinadas 174 coordenadas de runtime do backend e 151 do Android release. O OSV retornou 14 coordenadas com 35 avisos distintos. O npm audit do PC retornou zero avisos. Esse resultado não garante ausência de vulnerabilidades; as bases têm cobertura e podem divergir dos fabricantes. [API do OSV](https://google.github.io/osv.dev/post-v1-querybatch/).

Os números abaixo representam correspondência de versão, não exploração comprovada. Nenhum exploit de CVE foi executado. Severidade é o rótulo do cadastro OSV/GitHub, não uma classificação de risco final do SamusChat. Atualizar usando BOM/SDK compatíveis, repetir a consulta e testar os dois clientes; as versões indicadas são referências mínimas para os avisos consultados, não recomendação de fixar indefinidamente nessas versões.

## Coordenadas com avisos

| Componente | Pacote resolvido | Versão | Avisos | Referência de atualização |
| --- | --- | --- | --- | --- |
| backend | `org.apache.logging.log4j:log4j-api` | 2.24.3 | 1 | 2.25.5 na linha 2.25; 2.26.1 na linha 2.26 |
| backend | `com.fasterxml.jackson.core:jackson-core` | 2.21.4 | 2 | 2.21.7 na linha 2.21, alinhada ao BOM Jackson |
| backend | `org.apache.tomcat.embed:tomcat-embed-core` | 10.1.55 | 3 | 10.1.58 na linha 10.1 |
| backend | `org.springframework:spring-webmvc` | 6.2.19 | 2 | OSV indica 7.0.9; avaliar atualização do BOM Spring Boot ou suporte da linha 6.2 |
| backend | `org.postgresql:postgresql` | 42.7.11 | 1 | 42.7.12 |
| backend | `com.google.protobuf:protobuf-java` | 3.23.2 | 1 | 3.25.5 na linha 3.x; atualizar SDK/BOM responsável |
| backend | `io.grpc:grpc-netty-shaded` | 1.55.1 | 1 | 1.75.0 para o aviso consultado; alinhar gRPC/Firebase SDK |
| backend | `com.google.guava:guava` | 31.1-jre | 2 | Aviso registra correção na família 32.0.0; selecionar versão JRE compatível e reconsultar OSV |
| backend | `io.netty:netty-codec-http` | 4.1.135.Final | 8 | 4.1.137.Final para cobrir os avisos das linhas HTTP consultadas |
| backend | `io.netty:netty-codec` | 4.1.135.Final | 1 | 4.1.136.Final para o aviso; alinhar os módulos na atualização Netty |
| backend | `io.netty:netty-handler` | 4.1.135.Final | 2 | 4.1.137.Final |
| backend | `com.fasterxml.jackson.core:jackson-databind` | 2.21.4 | 9 | 2.21.7 na linha 2.21, alinhada ao BOM Jackson |
| Android | `com.google.code.gson:gson` | 2.8.5 | 1 | 2.8.9 é a primeira correção indicada; escolher versão compatível mantida |
| Android | `org.java-websocket:Java-WebSocket` | 1.3.6 | 1 | 1.5.0 é a primeira correção indicada; excluir se realmente sem uso ou atualizar |

## Aplicabilidade observada no código

- **Android / Java-WebSocket:** o pacote transitivo contém falha conhecida de hostname em TLS, mas `WebSocketManager` escolhe `ConnectionProvider.OKHTTP`. Não foi demonstrado uso desse cliente vulnerável no fluxo atual. Retirar o pacote exige testar a biblioteca STOMP, que também inclui sua implementação alternativa. [Aviso do fabricante](https://github.com/TooTallNate/Java-WebSocket/security/advisories/GHSA-gw55-jm4h-x339).
- **Android / Gson:** há uso real de `Gson.fromJson` em mensagens, mas o aviso consultado trata de desserialização Java de classes internas; isso não prova exploração por JSON comum. Não foi encontrado caminho de `ObjectInputStream` no código principal auditado. Atualizar a versão mesmo assim e validar REST/STOMP. [Correção do Gson](https://github.com/google/gson/pull/1991).
- **Tomcat:** o cadastro OSV marca avisos como críticos, enquanto o fabricante classifica o replay DIGEST como baixo e informa requisitos específicos nos demais avisos. O app usa Bearer/Spring Security; não foi identificado autenticador DIGEST/FORM do contêiner nem configurações `web.xml` correspondentes. A versão coincide, mas a exploração desses caminhos não foi demonstrada. O fabricante também lista avisos que a consulta OSV pode não retornar. [Avisos Tomcat 10](https://tomcat.apache.org/security-10.html).
- **Spring MVC:** não foram encontrados `XsltView`, renderização de fragmentos SSE ou configuração semelhante no código principal. Não declarar que qualquer usuário consegue explorar os avisos só pela presença de Spring MVC.
- **Jackson:** os DTOs auditados usam principalmente strings/IDs. Não foram encontrados ativação de default typing, `@JsonView`, campos `InetAddress` ou caminhos de tipos descritos por parte dos avisos. Manter a atualização e testar parsing/limites; não presumir que todos os cenários de desserialização sejam alcançáveis.
- **Protobuf/gRPC/Guava:** dependências presentes no backend, inclusive transitivas do Firebase Admin. A API local tinha Firebase desligado. Acesso a caminhos vulneráveis em push/serviços externos não foi testado.
- **Netty:** o servidor HTTP principal usa Tomcat. Avisos de SPDY, Bzip2, SNI e pipelines específicos de Netty precisam de análise das integrações que os utilizem. Netty sombreado em gRPC não é corrigido apenas por atualizar módulos Netty comuns.
- **PostgreSQL JDBC:** o aviso envolve negociação de channel binding/certificados; a API de ataque usou H2. Conexão TLS com PostgreSQL de produção permanece pendente de validação.

A análise de alcance é uma revisão do código principal, não análise de todo bytecode transitivo. As versões precisam ser atualizadas ou ter uma justificativa de não aplicabilidade registrada e revisada. Binários nativos internos de WebRTC/SDKs, SO, imagens Docker e histórico Git não estão cobertos por esta consulta Maven/npm.

## Avisos retornados

| Aviso | Identificadores | Severidade do cadastro | Descrição abreviada |
| --- | --- | --- | --- |
| [GHSA-qv9r-c865-cp47](https://nvd.nist.gov/vuln/detail/CVE-2026-49844) | CVE-2026-49844 | MODERATE | Apache Log4j API: Improper encoding of non-finite floating-point values during MapMessage JSON serialization |
| [GHSA-7hhh-6rmp-j9qf](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-7hhh-6rmp-j9qf) | CVE-2026-89425 | HIGH | jackson-core: UTF8DataInputJsonParser._reportInvalidToken() missing maxErrorTokenLength limit -> unbounded StringBuilder growth (DoS) |
| [GHSA-p6pp-m3f8-5c89](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-p6pp-m3f8-5c89) | CVE-2026-89407 | HIGH |  jackson-core: ReDoS: quadratic backtracking in NumberInput.PATTERN_FLOAT via looksLikeValidNumber() |
| [GHSA-9xv2-5v5q-p794](https://nvd.nist.gov/vuln/detail/CVE-2026-65905) | CVE-2026-65905 | CRITICAL | Apache Tomcat's DIGEST authenticator has an Authentication Bypass by Capture-replay vulnerability |
| [GHSA-gcx9-497g-6cp6](https://tomcat.apache.org/security-10.html) | CVE-2026-65182 | CRITICAL | Apache Tomcat has an Improper Access Control, Incorrect Authorization vulnerability |
| [GHSA-h3x4-894j-xpx5](https://tomcat.apache.org/security-10.html) | CVE-2026-68525 | CRITICAL | Apache Tomcat's FORM authentication process has an Incorrect Authorization vulnerability |
| [GHSA-j9f9-w8pj-32f8](https://spring.io/security/cve-2026-47890) | CVE-2026-47890 | CRITICAL | Spring Framework Server Sent Event stream corruption while rendering fragments |
| [GHSA-pc63-qcmh-9cmg](https://spring.io/security/cve-2026-47884) | CVE-2026-47884 | CRITICAL | Spring Framework Improper Path Limitation in XsltView |
| [GHSA-j92g-9f8w-j867](https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-j92g-9f8w-j867) | CVE-2026-54291 | HIGH | PostgreSQL JDBC Driver: Silent channel-binding authentication downgrade via unsupported certificate algorithms |
| [GHSA-735f-pc8j-v9w8](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-735f-pc8j-v9w8) | CVE-2024-7254 | HIGH | protobuf-java has potential Denial of Service issue |
| [GHSA-prj3-ccx8-p6x4](https://github.com/netty/netty/security/advisories/GHSA-prj3-ccx8-p6x4) | CVE-2025-55163 | HIGH | Netty affected by MadeYouReset HTTP/2 DDoS vulnerability |
| [GHSA-5mg8-w23w-74h3](https://nvd.nist.gov/vuln/detail/CVE-2020-8908) | CVE-2020-8908 | LOW | Information Disclosure in Guava |
| [GHSA-7g45-4rm6-3mm3](https://nvd.nist.gov/vuln/detail/CVE-2023-2976) | CVE-2023-2976 | MODERATE | Guava vulnerable to insecure use of temporary directory |
| [GHSA-4mp9-239f-g9hg](https://github.com/netty/netty/security/advisories/GHSA-4mp9-239f-g9hg) | CVE-2026-59898 | MODERATE | Netty: WebSockets V07/V08 handshaker missing Connection/Upgrade validation |
| [GHSA-6cqp-g7gg-8hr5](https://github.com/netty/netty/security/advisories/GHSA-6cqp-g7gg-8hr5) | CVE-2026-56746 | MODERATE | Netty: Security Control Bypass via CORS Short-Circuit Failure |
| [GHSA-6jqx-86gh-f27w](https://github.com/netty/netty/security/advisories/GHSA-6jqx-86gh-f27w) | CVE-2026-55831 | HIGH | Netty SPDY SETTINGS frame count materializes unbounded settings map |
| [GHSA-8c42-7qj2-3j46](https://github.com/netty/netty/security/advisories/GHSA-8c42-7qj2-3j46) | CVE-2026-59903 | MODERATE | Netty Vulnerable to Cache Poisoning and Information Disclosure via CORS Vary Header Overwrite |
| [GHSA-gcjf-9mgh-3p7g](https://github.com/netty/netty/security/advisories/GHSA-gcjf-9mgh-3p7g) | CVE-2026-59921 | MODERATE | Netty: CRLF Injection via Multipart Filename in Netty HttpPostRequestEncoder |
| [GHSA-jppx-w49h-x2qq](https://github.com/netty/netty/security/advisories/GHSA-jppx-w49h-x2qq) | CVE-2026-56745 | HIGH | Netty: [SpdyHttpDecoder] ByteBuf Reference Leak on RST_STREAM Leads to Native Memory Exhaustion |
| [GHSA-mvh2-crg5-v77c](https://github.com/netty/netty/security/advisories/GHSA-mvh2-crg5-v77c) | CVE-2026-55833 | HIGH | Netty SPDY zlib header block continues decoded expansion after maxHeaderSize truncation |
| [GHSA-q4f6-jm68-57ww](https://github.com/netty/netty/security/advisories/GHSA-q4f6-jm68-57ww) | CVE-2026-59899 | MODERATE | Netty: [HttpContentEncoder] Unbounded Per-Connection Queue Growth via HTTP/1.1 Pipelining Leads to Denial of Service |
| [GHSA-558v-64gr-wgg4](https://github.com/netty/netty/security/advisories/GHSA-558v-64gr-wgg4) | CVE-2026-59901 | HIGH | Netty: [Bzip2Decoder] Infinite Loop in RLE State Machine Leads to Event-Loop Thread Hang |
| [GHSA-c4c3-7fpv-j4q5](https://github.com/netty/netty/security/advisories/GHSA-c4c3-7fpv-j4q5) | CVE-2026-75595 | CRITICAL | Netty: SNI Routing Bypass via Fragmented TLS ClientHello Causing Fallback to Default SslContext |
| [GHSA-fccg-mwvh-qqg4](https://github.com/netty/netty/security/advisories/GHSA-fccg-mwvh-qqg4) | CVE-2026-75596 | MODERATE | Netty: Fragmented ClientHello records trigger quadratic pre-handshake reassembly in default SNI parsing |
| [GHSA-5gvw-p9qm-jgwh](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5gvw-p9qm-jgwh) | CVE-2026-59889 | MODERATE | jackson-databind: @JsonView bypassed for @JsonUnwrapped container properties on deserialization |
| [GHSA-5jmj-h7xm-6q6v](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5jmj-h7xm-6q6v) | CVE-2026-54515 | MODERATE | jackson-databind has case-insensitive deserialization bypasses per-property @JsonIgnoreProperties |
| [GHSA-cxp5-3px4-pw24](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-cxp5-3px4-pw24) | CVE-2026-91777 | HIGH | jackson-databind quadratic forward-reference completion  |
| [GHSA-gx83-3vf8-gh7j](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-gx83-3vf8-gh7j) | CVE-2026-83557 | MODERATE | jackson-databind: Comparable missing from DefaultBaseTypeLimitingValidator's unsafe base types (incomplete PolymorphicTypeValidator denylist) |
| [GHSA-mhm7-754m-9p8w](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-mhm7-754m-9p8w) | Sem CVE no registro | MODERATE | jackson-databind: `@JsonView` bypass for creator properties with `@JsonTypeInfo(include=As.EXTERNAL_PROPERTY)` |
| [GHSA-q4xh-88c3-wmh7](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-q4xh-88c3-wmh7) | CVE-2026-68497 | HIGH | jackson-databind: Duration XMLGregorianCalendar Unbounded Number Parse DoS |
| [GHSA-vvgp-rfg2-7rr6](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-vvgp-rfg2-7rr6) | CVE-2026-77310 | MODERATE | jackson-databind: Incomplete fix for CVE-2026-54514: eager DNS resolution (SSRF) still present in InetAddress deserialization |
| [GHSA-wjgm-6hv5-3cvf](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-wjgm-6hv5-3cvf) | CVE-2026-19032 | MODERATE | jackson-databind: Path Deserialization Missing Scheme Allowlist for FileSystemProvider Resolution |
| [GHSA-wv8q-qhhj-9h54](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-wv8q-qhhj-9h54) | CVE-2026-91776 | HIGH | jackson-databind retains every unknown raw type ID  |
| [GHSA-4jrv-ppp4-jm57](https://nvd.nist.gov/vuln/detail/CVE-2022-25647) | CVE-2022-25647 | HIGH | Deserialization of Untrusted Data in Gson |
| [GHSA-gw55-jm4h-x339](https://github.com/TooTallNate/Java-WebSocket/security/advisories/GHSA-gw55-jm4h-x339) | CVE-2020-11050 | HIGH | Improper Validation of Certificate with Host Mismatch in Java-WebSocket |

O inventário e as faixas de versão recebidas estão em [dependency-results.json](dependency-results.json). O relatório principal apresenta as falhas realmente reproduzidas e seus critérios de correção: [auditoria PC/Android](../../AUDITORIA_SEGURANCA_PC_ANDROID_2026-10-12.md).
