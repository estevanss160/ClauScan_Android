# ClauScan Android — build APK na nuvem

Projeto Android complementar ao ClauScan Windows. Você não precisa instalar Android Studio no seu computador: o GitHub Actions compila o APK na nuvem.

## Gerar o APK sem Android Studio

1. Crie uma conta gratuita no GitHub, se ainda não tiver.
2. No GitHub, crie um repositório novo chamado `ClauScan-Android`.
3. Escolha **Add file > Upload files** e envie todos os arquivos e pastas deste ZIP (incluindo `.github/workflows/build-apk.yml`).
   - Importante: o caminho do arquivo de workflow deve ficar exatamente `.github/workflows/build-apk.yml`.
4. Faça o commit dos arquivos.
5. No repositório, abra a aba **Actions**.
6. Se aparecer a mensagem para habilitar workflows, clique para habilitar.
7. Selecione **Build ClauScan APK** e clique em **Run workflow** (ou aguarde o build disparado pelo commit).
8. Quando terminar com sucesso, abra a execução e baixe o artifact **ClauScan-debug-apk**.
9. Extraia o artifact ZIP; dentro estará `app-debug.apk`.
10. Envie o APK para o celular e instale. Se o Android bloquear, autorize a instalação pelo aplicativo usado para abrir o arquivo.

## Aplicativo
- Android 8.0 (API 26) ou superior.
- Câmera para ler o QR Code de pareamento e códigos de barras.
- Conexão WebSocket com ClauScan Windows v1 na mesma rede Wi-Fi.
- QR Code Windows contém endereço `ws://...:8765` e token.

## Atenção
Este pacote contém o projeto e a configuração de build na nuvem, não o APK binário pré-compilado. O APK só ficará disponível depois que o workflow for executado com sucesso no GitHub.
