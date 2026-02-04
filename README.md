# Steam Wishlist Monitor 🎮

Um monitor automatizado de preços e promoções para a wishlist da loja Steam, desenvolvido para rodar 24/7 em dispositivos de baixo consumo.

## 🚀 Sobre o Projeto
Este projeto monitora listas de desejos ou jogos específicos na Steam Wishlist e notifica quando o preço atinge um valor alvo ou quando surgem novas promoções relevantes. O sistema foi projetado para ser leve e eficiente.

## 🛠️ Tecnologias Utilizadas
* **Linguagem:** Java 25 (Preview Features)
* **Target Hardware:** Raspberry Pi 5
* **IDE:** IntelliJ IDEA
* **Build System:** Maven/Gradle (ajustar conforme seu projeto)

## 📋 Pré-requisitos
* JDK 25 instalado e configurado no `PATH`.
* Acesso à internet para realizar o scraping/monitoramento.

## ⚙️ Configuração
1.  Clone o repositório:
    ```bash
    git clone [https://github.com/paulloestevam/steam-wishlist-monitor.git](https://github.com/paulloestevam/steam-wishlist-monitor.git)
    ```
2.  Configure o arquivo de propriedades (crie um arquivo `config.properties` baseado no exemplo, se houver):
    ```properties
    target.url=[https://www.steam-wishlist.com/](https://www.steam-wishlist.com/)...
    notification.email=seuemail@exemplo.com
    ```
3.  Execute o projeto através da sua IDE ou via terminal.

## 🤝 Contribuição
Contribuições são bem-vindas! Sinta-se à vontade para abrir Issues ou Pull Requests.

---
Desenvolvido por [Paulo Estevam](https://github.com/paulloestevam)