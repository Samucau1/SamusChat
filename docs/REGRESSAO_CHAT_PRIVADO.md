# Regressão do chat privado

Data: 05/10/2026.

- API: suíte completa com 110 testes, zero falhas, zero erros e um ignorado.
- Android: 24 testes, zero falhas e zero erros; APK debug compilado com sucesso.
- Flyway: esquema V5 validado pelo Hibernate, sem migrações pendentes.
- Mensagens privadas: testes HTTP verificam envio, persistência, leitura pelo destinatário, isolamento de terceira conta, autenticação e validação de texto.
- API local atualizada na porta 8080: contas de teste autenticadas; envio, resposta, leitura do histórico e isolamento de terceira conta confirmados no banco habitual.
- Git: verificação de espaços em branco concluída sem erros.

O teste de navegação e toques pela interface não foi executado: nenhum celular ou emulador estava conectado ao ADB. Os testes Android são unitários; não substituem uma verificação visual da abertura direta da conversa.

Logs e relatórios JUnit ficam nas pastas de build ignoradas pelo Git. Credenciais e tokens das contas locais de teste não fazem parte deste documento.
