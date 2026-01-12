# 🏗️ W2A Builder - Gerador de APK/AAB para Android

<div align="center">

![GitHub](https://img.shields.io/badge/version-1.0.0-blue)
![Android](https://img.shields.io/badge/Android-✓-green)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-✓-2088FF)
![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)

**Transforme sites e jogos HTML5 em aplicativos Android nativos com um clique**

[🚀 Usar Agora](#-como-usar) | [📖 Documentação](#-funcionalidades) | [🤝 Contribuir](#-contribuição)

</div>

## 📱 Sobre o Projeto

O **W2A Builder** é um sistema SaaS completo que permite converter sites, jogos HTML5 e aplicações web em aplicativos Android nativos (APK/AAB) de forma totalmente automatizada. Ideal para desenvolvedores, educadores e empresas que precisam distribuir conteúdo web como aplicativos móveis.

### 🎯 Características Principais

- **Conversão automática** de HTML5 para APK/AAB Android
- **Interface web intuitiva** com upload via URL
- **Builds em nuvem** usando GitHub Actions
- **Assinatura automática** ou com keystore personalizada
- **Monitoramento em tempo real** do processo de build
- **Downloads públicos** sem necessidade de login

## ✨ Funcionalidades

| Funcionalidade | Descrição | Status |
|----------------|-----------|--------|
| **Build Duplo** | Gera simultaneamente APK (instalação) e AAB (Play Store) | ✅ |
| **Personalização** | Nome, ícone, package name e versão customizáveis | ✅ |
| **Keystore Flexível** | Automática para apps novos ou personalizada para updates | ✅ |
| **Monitoramento** | Página com animação e progresso em tempo real | ✅ |
| **Releases GitHub** | Downloads públicos sem necessidade de login | ✅ |
| **Validação** | Verificação de formato de package name e URLs | ✅ |
| **Sistema de Doações** | Suporte via PIX e Vakinha para manter o projeto | ✅ |

## 🛠️ Tecnologias Utilizadas

### Backend & Infraestrutura
- **PHP 7.4+** - Processamento do formulário e callbacks
- **GitHub Actions** - Pipeline de build automatizado
- **GitHub API** - Gerenciamento de releases e dispatches
- **cURL** - Comunicação com APIs externas

### Frontend
- **HTML5/CSS3** - Interface responsiva e moderna
- **JavaScript (ES6+)** - Validações e interações
- **Gradientes e Animações CSS** - Efeitos visuais
- **Design Responsivo** - Compatível com mobile e desktop

### Build Android
- **Android Gradle** - Build nativo de APK/AAB
- **Java/Kotlin** - Código base do WebView
- **Android SDK** - Ferramentas oficiais do Android
- **Keystore Management** - Assinatura de aplicativos

## 🚀 Como Usar

### Para Usuários Finais

1. **Acesse o formulário** 
   - Vá para a página principal do W2A Builder

2. **Preencha os dados do app**
   ```
   📱 Nome do Aplicativo: Meu Jogo Incrível
   🏷️ Package Name: com.empresa.jogo
   🔢 Versão: 1.0.0
   📦 URL do ZIP: https://exemplo.com/jogo.zip  
   🖼️ URL do Ícone: https://exemplo.com/icone.webp  
   🔐 Tipo de Assinatura: Automática ou Personalizada
   ```

3. **Envie para build**
   - Clique em "GERAR APK E AAB"
   - Aguarde o processamento (2-5 minutos)
   - Acompanhe o progresso na página de status

4. **Baixe os arquivos**
   - Quando pronto, acesse a release no GitHub
   - Baixe APK (instalação direta) e/ou AAB (Play Store)

### Tipos de Assinatura

| Tipo | Recomendado Para | Vantagens |
|------|------------------|-----------|
| **🔄 Automática** | Apps novos | Simples, não precisa de configuração |
| **🔐 Personalizada** | Atualizações de apps existentes | Mantém mesma assinatura da Play Store |

## ⚙️ Configuração para Desenvolvimento

### Pré-requisitos
- Servidor web com PHP 7.4+
- Acesso SSH ao servidor
- Conta no GitHub com repositório W2A
- Token de acesso pessoal do GitHub

### Passos de Instalação

1. **Clone o repositório**
   ```bash
   git clone https://github.com/lorinhojr/w2a.git  
   cd w2a
   ```

2. **Configure as permissões**
   ```bash
   chmod -R 755 ./
   mkdir build_logs
   chmod 777 build_logs
   ```

3. **Configure as variáveis**
   Edite `index.php` e `check_release.php`:
   ```php
   $token = "seu_token_github_aqui";
   $base_url = "https://seu-dominio.com/w2a";
   ```

4. **Configure o GitHub Actions**
   - No repositório, vá em Settings → Actions → General
   - Habilite "Read and write permissions"
   - Adicione secrets se necessário:
     - `SIGNING_KEY` (opcional)
     - `KEY_PASSWORD` (opcional)
     - `ALIAS` (opcional)

5. **Teste a instalação**
   ```bash
   php -S localhost:8000
   ```
   Acesse `http://localhost:8000`

## 📁 Estrutura do Projeto

```
w2a/
├── .github/workflows/
│   └── main.yml              # Pipeline de build do GitHub Actions
├── app/                      # Projeto Android base
│   ├── src/main/
│   │   ├── java/            # Código Java/Kotlin
│   │   ├── res/             # Recursos Android
│   │   └── assets/www/      # HTML/JS/CSS dos usuários
│   ├── build.gradle.kts     # Configuração do build
│   └── AndroidManifest.xml  # Manifest do Android
├── public_html/              # Arquivos PHP do sistema web
│   ├── index.php            # Formulário principal
│   ├── status.php           # Monitoramento de builds
│   ├── check_release.php    # Verificador de releases
│   ├── error.php            # Página de erros
│   └── build_logs/          # Logs de execução
├── README.md                 # Esta documentação
└── LICENSE                   # Licença do projeto
```

## 🔧 Workflow do GitHub Actions

O sistema utiliza um workflow automatizado com as seguintes etapas:

1. **Trigger**: Disparado via `repository_dispatch`
2. **Setup**: Configura Java, Android SDK e dependências
3. **Personalização**: Substitui placeholders com dados do usuário
4. **Build**: Compila APK (debug/release) e AAB
5. **Assinatura**: Usa keystore automática ou personalizada
6. **Release**: Cria release pública no GitHub com os arquivos
7. **Notificação**: Atualiza status para o usuário

## 🎨 Design e UX

### Interface do Usuário
- **Tema escuro futurista** com gradientes azuis
- **Animações CSS** suaves e profissionais
- **Feedback visual** em tempo real
- **Design responsivo** para todos os dispositivos
- **Partículas animadas** em segundo plano

### Experiência do Build
- **Progresso visual** com animação de construção
- **Tempo estimado** exibido em tempo real
- **Etapas detalhadas** do processo
- **Notificações** quando o build está pronto
- **Links diretos** para download

## 🔒 Segurança

### Proteção de Dados
- **Keystores pessoais** processadas em memória temporária
- **Token GitHub** com permissões mínimas necessárias
- **Validação de inputs** para prevenir injeção
- **Base64 encoding** para transferência segura de arquivos
- **Logs temporários** apagados após processamento

### Boas Práticas Implementadas
- Não armazenamento de keystores dos usuários
- Validação de tipos de arquivo
- Sanitização de inputs
- Timeouts em chamadas externas
- Tratamento de erros com mensagens amigáveis

## 🤝 Contribuição

Contribuições são bem-vindas! Para contribuir com o projeto:

1. **Faça um Fork** do repositório
2. **Crie uma Branch** para sua feature
   ```bash
   git checkout -b feature/nova-feature
   ```
3. **Desenvolva sua feature** com testes
4. **Commit suas mudanças**
   ```bash
   git commit -m 'Adiciona nova feature incrível'
   ```
5. **Push para a Branch**
   ```bash
   git push origin feature/nova-feature
   ```
6. **Abra um Pull Request**

### Áreas que Precisam de Ajuda
- ✅ Melhorias na interface do usuário
- ✅ Novas funcionalidades de build
- ✅ Otimização do workflow
- ✅ Traduções para outros idiomas
- ✅ Testes automatizados

## 📄 Licença GPL-3.0

Este projeto está licenciado sob a **GNU General Public License v3.0**.

### Direitos Garantidos pela GPL-3.0:
- ✅ **Liberdade 0**: Executar o software para qualquer propósito
- ✅ **Liberdade 1**: Estudar como o software funciona e adaptá-lo
- ✅ **Liberdade 2**: Redistribuir cópias
- ✅ **Liberdade 3**: Distribuir versões modificadas

### Obrigações para Derivados:
- 📋 Qualquer trabalho derivado DEVE usar a mesma licença GPL-3.0
- 📋 O código-fonte completo DEVE estar disponível
- 📋 Avisos de copyright e licença DEVEM ser preservados
- 📋 Mudanças significativas DEVEM ser documentadas

### Para Usuários Finais:
Este software é fornecido **"COMO ESTÁ"**, sem garantia de qualquer tipo. Veja o arquivo [LICENSE](LICENSE) para o texto completo da licença.

## 💖 Apoie o Projeto

O W2A Builder é mantido com ❤️ por **Júnior G. Teixeira**. Se o projeto te ajudou, considere apoiar:

### 📲 PIX
- **Nome**: Júnior G. Teixeira
- **Chave**: (55) 99237-9133
- **Banco**: Inter

### 🎗️ Vakinha
Apoie o desenvolvimento contínuo:
[https://www.vakinha.com.br/vaquinha/zurl-engine](https://www.vakinha.com.br/vaquinha/zurl-engine)

### Outras Formas de Apoiar
- ⭐ **Dê uma estrela** no repositório
- 🔄 **Compartilhe** com outros desenvolvedores
- 🐛 **Reporte bugs** e issues
- 💡 **Sugira melhorias** e novas features

## 📞 Suporte e Contato

### Issues e Bugs
- Abra uma **Issue** no GitHub
- Descreva o problema detalhadamente
- Inclua screenshots se possível

### Dúvidas e Sugestões
- Use as **Discussions** do GitHub
- Entre em contato via email do mantenedor

### Status do Serviço
- **Uptime**: 99.9%
- **Tempo médio de build**: 3-5 minutos
- **Limites**: Sem limites de uso para usuários gratuitos

## 🚨 Solução de Problemas

### Problemas Comuns

| Problema | Causa | Solução |
|----------|-------|---------|
| Erro 422 no build | Payload muito grande | Use keystore_info agrupado |
| Build falhando | Package name inválido | Use formato: com.exemplo.app |
| Release não aparece | Permissões insuficientes | Configure `contents: write` |
| APK não assina | Keystore ausente | Use automática ou forneça .jks |

### Debug do Workflow
1. Verifique os logs do GitHub Actions
2. Confirme as variáveis de ambiente
3. Valide o formato do ZIP do usuário
4. Verifique permissões do repositório

## 🔮 Roadmap

### Próximas Features
- [ ] **Integração com Google Play API** - Upload direto para Play Store
- [ ] **Mais templates** - Opções adicionais de WebView
- [ ] **API REST** - Builds programáticos via API
- [ ] **Sistema de usuários** - Histórico de builds
- [ ] **Notificações por email** - Status do build por email
- [ ] **Cache de builds** - Builds mais rápidos para projetos repetidos

### Melhorias Planejadas
- [ ] **Build incremental** - Para projetos grandes
- [ ] **Mais opções de assinatura** - Suporte a mais tipos de keystore
- [ ] **Interface multi-idioma** - Inglês, Espanhol, etc.
- [ ] **Dashboard administrativo** - Para gerenciar múltiplos projetos

## 🙏 Agradecimentos

Um agradecimento especial para:

- **Comunidade GitHub** pelas Actions e hospedagem
- **Contribuidores** que ajudaram com código e sugestões
- **Usuários** que testam e reportam issues
- **Apoiadores financeiros** que mantêm o projeto vivo

---

<div align="center">

**Feito com ❤️ para a comunidade de desenvolvedores**

[🏠 Página Inicial](https://github.com/lorinhojr/w2a) | [📖 Documentação](#) | [🐛 Reportar Bug](https://github.com/lorinhojr/w2a/issues)

</div>
