SamusChat — Sistema de Chat em Tempo Real
Visão Geral do Projeto
O SamusChat é um sistema de comunicação em tempo real inspirado no Discord, desenvolvido com tecnologias modernas e arquitetura profissional voltada para escalabilidade, segurança e alta disponibilidade. O objetivo do projeto é criar uma plataforma robusta capaz de suportar comunicação instantânea entre usuários através de servidores, canais e mensagens em tempo real.
O sistema será composto por uma aplicação Android responsável pela interface do usuário e um back-end desenvolvido em Java com Spring Boot, responsável por toda a lógica de negócio, autenticação, comunicação em tempo real e persistência de dados.
A arquitetura foi planejada seguindo princípios de separação de responsabilidades, permitindo fácil manutenção, evolução do sistema e futura escalabilidade horizontal.
________________________________________
Tecnologias Utilizadas
O sistema utilizará uma stack moderna e escalável.
Back-end
O back-end será desenvolvido utilizando Java 21 juntamente com Spring Boot. Essa combinação permite alta produtividade, organização do código e integração facilitada com mecanismos de segurança, banco de dados e comunicação em tempo real.
O Spring Boot será responsável pela criação da API REST e gerenciamento dos serviços internos da aplicação.
________________________________________
Segurança
A segurança será implementada utilizando Spring Security combinado com JWT (JSON Web Token).
O JWT será utilizado para autenticação stateless, permitindo que o servidor valide usuários sem necessidade de armazenar sessões em memória.
As senhas dos usuários serão protegidas utilizando BCrypt, garantindo que nenhuma senha seja armazenada em texto puro no banco de dados.
________________________________________
Banco de Dados
O banco de dados principal será o PostgreSQL 16.
Ele será responsável pelo armazenamento persistente de:
•	usuários
•	mensagens
•	servidores
•	canais
•	permissões
•	cargos
O PostgreSQL foi escolhido devido à sua robustez, estabilidade e excelente suporte para aplicações escaláveis.
________________________________________
Redis
O Redis será utilizado para operações em tempo real.
Sua principal função será:
•	gerenciamento de sessões
•	cache
•	distribuição de eventos utilizando Pub/Sub
•	sincronização entre múltiplas instâncias do servidor
________________________________________
Mobile
O aplicativo Android será desenvolvido utilizando Kotlin.
A aplicação será responsável por:
•	autenticação do usuário
•	interface gráfica
•	envio e recebimento de mensagens
•	gerenciamento de servidores e canais
A comunicação com o servidor ocorrerá através de REST API e WebSocket.
________________________________________
Arquitetura do Sistema
A arquitetura do sistema será baseada em separação de responsabilidades.
O cliente Android se comunicará com o servidor através de dois mecanismos principais:
•	REST API
•	WebSocket
A REST API será utilizada para operações tradicionais, enquanto o WebSocket será responsável pela comunicação em tempo real.
________________________________________
Fluxo Geral da Arquitetura
O aplicativo Android enviará requisições HTTP para o servidor Spring Boot.
O Spring Boot atuará como núcleo central do sistema, processando autenticação, mensagens, permissões e gerenciamento dos dados.
O PostgreSQL será responsável pelo armazenamento persistente das informações.
O Redis será utilizado para distribuição de eventos em tempo real.
Essa arquitetura permitirá baixa latência e suporte a múltiplos usuários simultaneamente.
________________________________________
Estrutura do Back-end
O back-end seguirá uma arquitetura em camadas.
Controller
Responsável por receber requisições HTTP.
Os controllers não devem conter lógica de negócio complexa.
Sua função será apenas receber dados e encaminhá-los para os serviços responsáveis.
________________________________________
Service
Camada responsável pela lógica de negócio do sistema.
Aqui ficarão regras como:
•	validação de permissões
•	autenticação
•	validação de mensagens
•	regras de acesso
________________________________________
Repository
Responsável pela comunicação com o banco de dados.
Será utilizado Spring Data JPA juntamente com Hibernate.
________________________________________
Entity
Representa os modelos do banco de dados.
Exemplos:
•	User
•	Message
•	Channel
•	Server
•	Role
________________________________________
DTO
Os DTOs serão utilizados para entrada e saída de dados da API.
Isso evita exposição direta das entidades do banco de dados.
________________________________________
Sistema de Autenticação
A autenticação será a base principal do sistema.
Nenhuma funcionalidade crítica poderá ser acessada sem autenticação válida.
________________________________________
Funcionamento do BCrypt
O BCrypt será utilizado para proteger senhas dos usuários.
As senhas nunca serão armazenadas em texto puro.
Quando um usuário criar uma conta:
1.	a senha será transformada em hash
2.	o hash será salvo no banco
3.	durante o login o BCrypt comparará os valores
Isso aumenta drasticamente a segurança do sistema.
________________________________________
Funcionamento do JWT
Após login bem-sucedido, o servidor gerará um token JWT.
Esse token será enviado ao aplicativo Android.
O Android armazenará esse token localmente e o enviará em todas as requisições futuras.
O token conterá:
•	identificação do usuário
•	validade
•	assinatura digital
Isso permite autenticação segura sem necessidade de sessões tradicionais.
________________________________________
Fluxo de Login
O usuário abrirá o aplicativo e enviará email e senha.
O servidor realizará as seguintes etapas:
1.	buscar usuário no banco de dados
2.	comparar senha utilizando BCrypt
3.	gerar token JWT
4.	retornar token para o cliente
Após isso o usuário estará autenticado.
________________________________________
Comunicação em Tempo Real
O sistema utilizará WebSocket para comunicação instantânea.
Sem WebSocket o aplicativo precisaria consultar constantemente o servidor para verificar novas mensagens.
Isso aumentaria consumo de rede e latência.
Com WebSocket a conexão permanece aberta continuamente.
Quando uma mensagem for enviada:
1.	o cliente enviará mensagem ao servidor
2.	o servidor processará a mensagem
3.	a mensagem será salva no banco
4.	Redis distribuirá o evento
5.	usuários conectados receberão instantaneamente
________________________________________
Sistema de Servidores e Canais
O sistema permitirá criação de servidores semelhantes ao modelo utilizado pelo Discord.
Cada servidor poderá possuir múltiplos canais.
Exemplos:
•	canal geral
•	canal jogos
•	canal tecnologia
Os servidores também possuirão:
•	membros
•	cargos
•	permissões
________________________________________
Sistema de Permissões
O sistema possuirá controle de acesso baseado em cargos.
Exemplo:
•	ADMIN
•	MODERATOR
•	USER
Cada cargo possuirá permissões específicas.
Exemplos de permissões:
•	enviar mensagens
•	deletar mensagens
•	criar canais
•	banir usuários
________________________________________
Estrutura do Banco de Dados
O banco possuirá tabelas principais responsáveis pela organização das entidades do sistema.
Principais tabelas:
•	users
•	servers
•	channels
•	messages
•	roles
•	permissions
•	server_members
Os relacionamentos permitirão associação entre usuários, servidores, canais e mensagens.
________________________________________
Segurança do Sistema
O sistema implementará múltiplas camadas de segurança.
Entre elas:
•	autenticação JWT
•	criptografia de senhas com BCrypt
•	validação de entrada
•	proteção contra SQL Injection
•	controle de permissões
•	proteção contra spam
•	rate limiting
________________________________________
Escalabilidade
A arquitetura foi projetada para suportar crescimento futuro.
O uso de Redis permitirá sincronização entre múltiplas instâncias do servidor.
Futuramente será possível adicionar:
•	load balancers
•	múltiplos servidores
•	containers Docker
•	orquestração com Kubernetes
________________________________________
Próximas Etapas do Projeto
O desenvolvimento seguirá uma ordem estratégica.
Etapa 1 — Autenticação
Implementação de:
•	login
•	registro
•	JWT
•	BCrypt
________________________________________
Etapa 2 — CRUD de Usuários
Implementação de:
•	perfil
•	edição de dados
•	listagem de usuários
________________________________________
Etapa 3 — Servidores e Canais
Implementação de:
•	criação de servidores
•	criação de canais
•	gerenciamento de membros
________________________________________
Etapa 4 — Mensagens
Implementação de:
•	envio de mensagens
•	histórico de mensagens
•	armazenamento persistente
________________________________________
Etapa 5 — WebSocket
Implementação de mensagens em tempo real utilizando WebSocket e Redis Pub/Sub.
________________________________________
Etapa 6 — Sistema de Permissões
Implementação de cargos e controle de acesso.
________________________________________
Etapa 7 — Upload de Arquivos
Implementação de envio de imagens e arquivos.
________________________________________
Conclusão
O SamusChat será um sistema moderno de comunicação em tempo real construído com arquitetura profissional, foco em segurança, escalabilidade e desempenho.
O projeto permitirá aprofundamento em áreas avançadas da engenharia de software, incluindo:
•	arquitetura distribuída
•	autenticação segura
•	comunicação em tempo real
•	sistemas concorrentes
•	banco de dados relacionais
•	segurança de aplicações
•	engenharia back-end com Java Spring Boot
Além de ser um excelente projeto para portfólio profissional, o sistema servirá como base sólida para evolução futura em aplicações de larga escala.
 

## Android — Etapa 11

A auditoria dos padrões, os complementos e os testes de regressão da etapa anterior estão em [Etapa 10 — Clean Code e Patterns](docs/ETAPA_10.md).

O aplicativo Kotlin/Jetpack Compose está em [samuschat-android](samuschat-android/README.md), com instruções de execução, configuração do Firebase e roteiro de validação em dois dispositivos.

## CI/CD e escalabilidade — Etapa 13

O backend possui pipeline GitHub Actions com testes, cobertura JaCoCo, validação da
stack Docker, publicação no GitHub Container Registry (GHCR) e deploy por SSH. A configuração de produção
usa três instâncias atrás do Nginx, Redis Pub/Sub, PostgreSQL com migrações Flyway e
uploads compartilhados. Consulte [Etapa 13 — CI/CD e escalabilidade](docs/ETAPA_13.md)
para preparar o servidor e configurar secrets, variáveis e o ambiente `production`.
