package br.com.critplay.demo;

import br.com.critplay.catalog.Game;
import java.util.*;

/** Stable per-person tastes, independent of the position of a game in the catalogue. */
final class DemoOpinions {
    private DemoOpinions() {}
    private record Details(String[] highlights,String criticism) {}
    private static Details details(String a,String b,String c,String criticism) {
        return new Details(new String[]{a,b,c},criticism);
    }
    private static final Map<String,Details> GAMES=Map.ofEntries(
        Map.entry("elden ring",details(
            "Sair da estrada e encontrar uma área inteira escondida foi a melhor parte.",
            "Trocar a arma e a distribuição dos atributos mudou completamente minha experiência com os chefes.",
            "A direção de arte faz até uma caminhada sem objetivo render uma descoberta memorável.",
            "A falta de orientação e a repetição de alguns encontros me cansaram; nem sempre senti vontade de insistir.")),
        Map.entry("red dead redemption 2",details(
            "As conversas no acampamento me aproximaram dos personagens mais do que várias missões principais.",
            "Passei muito tempo só cavalgando e observando os pequenos acontecimentos do mundo.",
            "A trajetória de Arthur deixou algumas das cenas mais marcantes da minha biblioteca.",
            "As animações demoradas e as missões com caminho muito definido prejudicaram meu ritmo.")),
        Map.entry("cyberpunk 2077",details(
            "Misturar hackeamento e furtividade deixou os confrontos bem mais interessantes para mim.",
            "As missões dos personagens secundários foram o que mais me fez querer continuar em Night City.",
            "A iluminação e a arquitetura da cidade criam uma atmosfera que dá vontade de explorar a pé.",
            "O excesso de informação na tela e algumas atividades repetitivas acabaram me afastando da campanha.")),
        Map.entry("hollow knight",details(
            "Reconhecer uma passagem antiga por outro caminho me deu uma ótima sensação de descoberta.",
            "Os chefes funcionaram como pequenos testes de leitura e paciência, com golpes bem definidos.",
            "A trilha e os cenários contam muito sobre Hallownest sem precisar interromper a exploração.",
            "Voltar até o chefe depois de perder e ficar sem saber para onde ir tiraram parte da diversão.")),
        Map.entry("hades",details(
            "Combinar bênçãos diferentes fez a tentativa de fuga seguinte parecer uma experiência nova.",
            "Voltar à Casa de Hades para ouvir os personagens virou parte da recompensa de cada tentativa de fuga.",
            "Experimentar armas me manteve interessado mesmo depois de várias tentativas de fuga frustradas.",
            "Repetir as mesmas regiões em cada tentativa de fuga ficou cansativo antes de a progressão me conquistar.")),
        Map.entry("stardew valley",details(
            "Planejar as plantações da próxima estação me envolveu mais do que eu esperava.",
            "Gostei de descobrir os hábitos dos moradores e ver as relações evoluírem aos poucos.",
            "Alternar entre pesca, mina e fazenda tornou cada sessão um pouco diferente.",
            "A energia limitada e o relógio correndo me fizeram sentir uma pressa que eu não procurava nesse tipo de jogo.")),
        Map.entry("baldur's gate 3",details(
            "Resolver situações pelo diálogo e depois descobrir outra saída foi o ponto alto da campanha.",
            "Usar o cenário no combate por turnos abriu soluções muito além de só escolher um ataque.",
            "As relações do grupo deram um peso especial às decisões que tomei durante a jornada.",
            "O volume de regras, inventários e turnos longos tornou algumas sessões mais trabalhosas do que divertidas.")),
        Map.entry("the witcher 3: wild hunt",details(
            "Um contrato que parecia simples se transformar numa escolha difícil foi o que mais me marcou.",
            "A atmosfera das vilas e das regiões me fez querer viajar sem usar tanto os atalhos.",
            "Os personagens e as consequências das missões secundárias sustentaram minha vontade de continuar.",
            "O combate e o excesso de pontos no mapa ficaram repetitivos para o tempo que eu queria dedicar.")),
        Map.entry("god of war",details(
            "Os diálogos durante a viagem construíram muito bem a relação entre Kratos e Atreus.",
            "O peso do machado e a possibilidade de alternar recursos deixaram o combate satisfatório.",
            "Voltar às áreas com novas habilidades me incentivou a procurar os caminhos que tinha deixado para trás.",
            "Alguns encontros repetidos e trechos de travessia lenta interromperam o ritmo para mim.")),
        Map.entry("marvel's spider-man remastered",details(
            "Atravessar Nova York pelas teias foi tão divertido que várias vezes esqueci da missão.",
            "Usar os aparelhos junto com os movimentos aéreos deixou os confrontos bem dinâmicos.",
            "Equilibrar a vida de Peter e o trabalho de herói deu um bom motivo para acompanhar a campanha.",
            "As atividades espalhadas pelo mapa começaram a parecer uma lista de tarefas depois de algumas horas.")),
        Map.entry("celeste",details(
            "Superar uma sala difícil com poucos movimentos precisos foi muito recompensador.",
            "A história de Madeline e a música deram significado às tentativas que eu precisava repetir.",
            "Gostei de como uma mecânica simples ganha novas possibilidades a cada capítulo.",
            "A sequência de tentativas e a exigência de precisão me cansaram, mesmo reconhecendo a qualidade dos controles.")),
        Map.entry("the last of us part i",details(
            "Os momentos de conversa entre Joel e Ellie ficaram comigo depois de encerrar a sessão.",
            "Administrar poucos recursos tornou cada encontro mais tenso do que eu esperava.",
            "Os ambientes contam pequenas histórias que recompensam olhar além do caminho principal.",
            "A estrutura linear e a repetição de alguns confrontos limitaram a experiência para o meu gosto."))
    );
    private static final String[] TASTES={"aventura,rpg","roguelike,metroidvania","rpg,aventura","ação,plataforma",
        "simulação","rpg","metroidvania,plataforma,roguelike","ação,aventura","rpg,aventura","plataforma,roguelike",
        "simulação,rpg","plataforma,metroidvania","ação,roguelike","aventura,metroidvania"};
    private static final String[] OPENINGS={
        "Entrei em %s buscando uma história para me envolver.","Minha curiosidade em %s era descobrir o que eu conseguiria dominar com prática.",
        "Joguei %s prestando mais atenção aos caminhos fora do objetivo principal.","Fui para %s querendo uma experiência que funcionasse bem nos controles.",
        "Experimentei %s em sessões curtas, sem pressa de terminar.","Em %s, o que eu mais queria era liberdade para montar meu jeito de jogar.",
        "Comecei %s de olho nas ideias e na personalidade do jogo.","Meu plano com %s era explorar bastante antes de pensar nas conquistas.",
        "O que me levou a %s foi a curiosidade sobre o mundo e seus personagens.","Passei por %s observando como o jogo recompensa melhorar a execução.",
        "Tirei %s do backlog para descobrir se combinava com meu momento.","Cheguei a %s comparando suas ideias com jogos que já conhecia.",
        "Escolhi %s para jogar entre outras coisas, em partidas menores.","Dei uma chance a %s e procurei aproveitar o cenário no meu ritmo."
    };
    private static final String[] CLOSINGS={
        "A história pesa bastante na minha avaliação.","Para mim, desafio precisa vir acompanhado de vontade de tentar de novo.",
        "Minha nota considera principalmente o prazer de descobrir coisas.","Um bom ritmo de ação faz diferença no meu gosto pessoal.",
        "Valorizo jogos que me deixam desacelerar depois do trabalho.","Gosto de sentir que minhas decisões mudam minha experiência.",
        "Personalidade e boas ideias contam mais para mim do que tamanho do mapa.","Nem sempre querer completar tudo significa que tudo seja igualmente divertido.",
        "Fico mais envolvida quando o mundo tem detalhes para interpretar.","Controles e ritmo são o que mais influenciam minha vontade de voltar.",
        "Talvez eu volte em outro momento; meu ritmo também muda a experiência.","A comparação com meus favoritos influenciou um pouco essa impressão.",
        "Prefiro quando uma sessão curta já deixa alguma satisfação.","Explorar só vale a pena para mim quando o caminho também diverte."
    };
    static double rating(int person,Game game) {
        int variation=Math.floorMod(person*5+key(game).hashCode(),7);
        String genre=game.getGenre()==null?"":game.getGenre().toLowerCase(Locale.ROOT);
        boolean affinity=!genre.isBlank()&&Arrays.asList(TASTES[person].split(",")).contains(genre);
        return Math.min(5,1.5+variation*0.5+(affinity?1:0));
    }
    static String review(int person,Game game) {
        double rating=rating(person,game);
        Details details=GAMES.getOrDefault(key(game),details(
            "Gostei de testar as possibilidades do jogo antes de decidir como seguir.",
            "Prestei atenção em como a experiência mudava de uma sessão para outra.",
            "Encontrei boas ideias enquanto explorava a proposta do jogo.",
            "Algumas partes não combinaram com o ritmo e o estilo de experiência que eu procurava."));
        String observation=rating<=2.5?details.criticism():details.highlights()[Math.floorMod(person+key(game).hashCode(),3)];
        String verdict=rating<=2?"Não me conquistou; reconheço qualidades, mas não é uma recomendação pessoal.":
            rating<=3?"Fiquei dividido e recomendaria conferir a proposta antes de comprar.":
            rating<=4?"O saldo foi positivo, com ressalvas que não estragaram a experiência.":
            "Entrou entre as experiências que eu recomendaria com facilidade.";
        return "[Resenha fictícia de demonstração] "+OPENINGS[person].formatted(game.getTitle())+" "+observation+" "+verdict+" "+CLOSINGS[person];
    }
    private static String key(Game game){return game.getTitle().strip().toLowerCase(Locale.ROOT);}
}
