package ru.spanish.learning;

import java.util.Locale;

final class CardVisualHint {
    private CardVisualHint() {}

    static String forCard(Store.Card card) {
        String spanish = card.spanish().toLowerCase(Locale.ROOT);
        String meaning = card.meaning().toLowerCase(Locale.ROOT);

        if (has(spanish, meaning,"собака","perro")) return "🐕";
        if (has(spanish, meaning,"кот","gato")) return "🐈";
        if (has(spanish, meaning,"лошад","caballo")) return "🐎";
        if (has(spanish, meaning,"корова","vaca")) return "🐄";
        if (has(spanish, meaning,"птиц","pájaro")) return "🐦";
        if (has(spanish, meaning,"рыб","pescado")) return "🐟";
        if (has(spanish, meaning,"дерево","árbol")) return "🌳";
        if (has(spanish, meaning,"цветок","flor")) return "🌷";
        if (has(spanish, meaning,"лес","bosque")) return "🌲";
        if (has(spanish, meaning,"море","mar")) return "🌊";
        if (has(spanish, meaning,"река","río")) return "🏞️";
        if (has(spanish, meaning,"гора","montaña")) return "⛰️";
        if (has(spanish, meaning,"пляж","playa")) return "🏖️";
        if (has(spanish, meaning,"дожд","lluvia")) return "🌧️";
        if (has(spanish, meaning,"снег","nieve")) return "❄️";
        if (has(spanish, meaning,"солн","sol")) return "☀️";
        if (has(spanish, meaning,"ветер","viento")) return "💨";
        if (has(spanish, meaning,"огонь","fuego","горяч")) return "🔥";
        if (has(spanish, meaning,"холод","frío","ледян")) return "🧊";
        if (has(spanish, meaning,"облако","nube")) return "☁️";
        if (has(spanish, meaning,"бур","tormenta")) return "⛈️";

        if (has(spanish, meaning,"мужчин","hombre")) return "👨";
        if (has(spanish, meaning,"женщин","mujer")) return "👩";
        if (has(spanish, meaning,"отец","padre")) return "👨‍👧";
        if (has(spanish, meaning,"мать","madre")) return "👩‍👧";
        if (has(spanish, meaning,"сын","hijo")) return "👦";
        if (has(spanish, meaning,"дочь","hija")) return "👧";
        if (has(spanish, meaning,"семь","familia")) return "👨‍👩‍👧‍👦";
        if (has(spanish, meaning,"друг","amigo")) return "🫂";
        if (has(spanish, meaning,"врач","médico")) return "🧑‍⚕️";
        if (has(spanish, meaning,"водител","conductor")) return "🧑‍✈️";
        if (has(spanish, meaning,"начальник","jefe","сотрудник","empleado")) return "🧑‍💼";

        if (has(spanish, meaning,"дом","casa")) return "🏠";
        if (has(spanish, meaning,"город","ciudad")) return "🏙️";
        if (has(spanish, meaning,"улиц","calle","проспект","avenida")) return "🛣️";
        if (has(spanish, meaning,"площад","plaza")) return "⛲";
        if (has(spanish, meaning,"парк","parque")) return "🌳";
        if (has(spanish, meaning,"мост","puente")) return "🌉";
        if (has(spanish, meaning,"магазин","tienda","рынок","mercado")) return "🏪";
        if (has(spanish, meaning,"школ","escuela")) return "🏫";
        if (has(spanish, meaning,"отел","hotel")) return "🏨";
        if (has(spanish, meaning,"ресторан","restaurante")) return "🍽️";
        if (has(spanish, meaning,"больниц","hospital")) return "🏥";
        if (has(spanish, meaning,"аптек","farmacia")) return "💊";
        if (has(spanish, meaning,"банк","banco")) return "🏦";
        if (has(spanish, meaning,"офис","oficina")) return "🏢";
        if (has(spanish, meaning,"аэропорт","aeropuerto")) return "✈️";
        if (has(spanish, meaning,"остановк","parada")) return "🚏";

        if (has(spanish, meaning,"машин","coche")) return "🚗";
        if (has(spanish, meaning,"автобус","colectivo")) return "🚌";
        if (has(spanish, meaning,"метро","subte")) return "🚇";
        if (has(spanish, meaning,"поезд","tren")) return "🚆";
        if (has(spanish, meaning,"самол","avión")) return "✈️";
        if (has(spanish, meaning,"велосипед","bicicleta")) return "🚲";
        if (has(spanish, meaning,"мото","moto")) return "🏍️";
        if (has(spanish, meaning,"кораб","barco")) return "🚢";
        if (has(spanish, meaning,"такси","taxi")) return "🚕";
        if (has(spanish, meaning,"дорога","camino","маршрут","ruta")) return "🛣️";
        if (has(spanish, meaning,"светофор","semáforo")) return "🚦";

        if (has(spanish, meaning,"кофе","café")) return "☕";
        if (has(spanish, meaning,"чай","té")) return "🍵";
        if (has(spanish, meaning,"молок","leche")) return "🥛";
        if (has(spanish, meaning,"вода","agua")) return "💧";
        if (has(spanish, meaning,"пиво","cerveza")) return "🍺";
        if (has(spanish, meaning,"вино","vino")) return "🍷";
        if (has(spanish, meaning,"сок","jugo")) return "🧃";
        if (has(spanish, meaning,"хлеб","pan")) return "🍞";
        if (has(spanish, meaning,"сыр","queso")) return "🧀";
        if (has(spanish, meaning,"яйц","huevo")) return "🥚";
        if (has(spanish, meaning,"рис","arroz")) return "🍚";
        if (has(spanish, meaning,"суп","sopa")) return "🍲";
        if (has(spanish, meaning,"салат","ensalada")) return "🥗";
        if (has(spanish, meaning,"куриц","pollo")) return "🍗";
        if (has(spanish, meaning,"помидор","tomate")) return "🍅";
        if (has(spanish, meaning,"картоф","papa")) return "🥔";
        if (has(spanish, meaning,"лук","cebolla")) return "🧅";
        if (has(spanish, meaning,"чеснок","ajo")) return "🧄";
        if (has(spanish, meaning,"яблок","manzana")) return "🍎";
        if (has(spanish, meaning,"банан","banana")) return "🍌";
        if (has(spanish, meaning,"апельс","naranja")) return "🍊";
        if (has(spanish, meaning,"лимон","limón")) return "🍋";
        if (has(spanish, meaning,"еда","comida","есть","comer","готовить","cocinar")) return "🍽️";
        if (has(spanish, meaning,"пить","beber","tomar")) return "🥤";

        if (has(spanish, meaning,"ключ","llave")) return "🔑";
        if (has(spanish, meaning,"телефон","teléfono","звон","llamar","llamada")) return "📱";
        if (has(spanish, meaning,"компьютер","computadora","программир","programar")) return "💻";
        if (has(spanish, meaning,"интернет","internet")) return "🌐";
        if (has(spanish, meaning,"книга","libro","читать","leer")) return "📖";
        if (has(spanish, meaning,"писать","escribir")) return "✍️";
        if (has(spanish, meaning,"фото","foto","fotograf")) return "📷";
        if (has(spanish, meaning,"фильм","película","filmar")) return "🎬";
        if (has(spanish, meaning,"музык","música","петь","cantar")) return "🎵";
        if (has(spanish, meaning,"деньги","dinero","платить","pagar","цена","precio","зарплат","sueldo")) return "💰";
        if (has(spanish, meaning,"покуп","comprar","compra","магазин")) return "🛒";
        if (has(spanish, meaning,"продав","vender","venta")) return "🏷️";
        if (has(spanish, meaning,"документ","documento","подпис","firmar")) return "📄";
        if (has(spanish, meaning,"паспорт","pasaporte")) return "🛂";
        if (has(spanish, meaning,"карта","tarjeta")) return "💳";
        if (has(spanish, meaning,"сообщен","mensaje","писать")) return "💬";
        if (has(spanish, meaning,"почт","correo","отправ","enviar")) return "📨";
        if (has(spanish, meaning,"подар","regalar")) return "🎁";
        if (has(spanish, meaning,"бронь","reserva","reservar")) return "📅";

        if (has(spanish, meaning,"бежать","correr")) return "🏃";
        if (has(spanish, meaning,"ходить","andar","caminar")) return "🚶";
        if (has(spanish, meaning,"плавать","nadar")) return "🏊";
        if (has(spanish, meaning,"танцевать","bailar")) return "💃";
        if (has(spanish, meaning,"прыгать","saltar")) return "🤸";
        if (has(spanish, meaning,"спать","dormir","dormido")) return "😴";
        if (has(spanish, meaning,"просып","despertar","вставать","levantarse")) return "⏰";
        if (has(spanish, meaning,"сидеть","sentarse")) return "🪑";
        if (has(spanish, meaning,"летать","volar","взлет","despegar")) return "🛫";
        if (has(spanish, meaning,"призем","aterrizar")) return "🛬";
        if (has(spanish, meaning,"водить","conducir")) return "🚘";
        if (has(spanish, meaning,"останав","parar","тормоз","frenar")) return "🛑";
        if (has(spanish, meaning,"ускор","acelerar","быстр","rápido")) return "⚡";
        if (has(spanish, meaning,"медлен","lento")) return "🐢";
        if (has(spanish, meaning,"работать","trabajar")) return "💼";
        if (has(spanish, meaning,"учиться","aprender","estudiar")) return "🎓";
        if (has(spanish, meaning,"думать","pensar","считать","creer")) return "🤔";
        if (has(spanish, meaning,"знать","saber","conocer")) return "🧠";
        if (has(spanish, meaning,"понимать","entender","comprender")) return "💡";
        if (has(spanish, meaning,"говор","hablar","decir","объяс","explicar")) return "🗣️";
        if (has(spanish, meaning,"слушать","escuchar","слышать","oír")) return "👂";
        if (has(spanish, meaning,"смотреть","mirar","видеть","ver","наблюдать","observar")) return "👀";
        if (has(spanish, meaning,"искать","buscar")) return "🔎";
        if (has(spanish, meaning,"находить","encontrar")) return "🎯";
        if (has(spanish, meaning,"ждать","esperar")) return "⌛";
        if (has(spanish, meaning,"помог","ayudar")) return "🤝";
        if (has(spanish, meaning,"любить","amar","querer")) return "❤️";
        if (has(spanish, meaning,"ненавид","odiar")) return "💢";
        if (has(spanish, meaning,"сме","reír")) return "😂";
        if (has(spanish, meaning,"плак","llorar")) return "😢";
        if (has(spanish, meaning,"улыб","sonreír")) return "😊";
        if (has(spanish, meaning,"крич","gritar")) return "📣";
        if (has(spanish, meaning,"молч","callar")) return "🤫";
        if (has(spanish, meaning,"целова","besar")) return "💋";
        if (has(spanish, meaning,"обним","abrazar")) return "🤗";
        if (has(spanish, meaning,"извин","disculparse","прощ","perdonar")) return "🙏";
        if (has(spanish, meaning,"благодар","agradecer")) return "🙏";
        if (has(spanish, meaning,"защищ","proteger","defender")) return "🛡️";
        if (has(spanish, meaning,"напад","atacar","драться","pelear")) return "🥊";
        if (has(spanish, meaning,"строить","construir")) return "🏗️";
        if (has(spanish, meaning,"ломать","romper","разруш","destruir")) return "💥";
        if (has(spanish, meaning,"чинить","arreglar","reparar")) return "🛠️";
        if (has(spanish, meaning,"рисовать","dibujar","pintar")) return "🎨";
        if (has(spanish, meaning,"редакт","editar")) return "✏️";
        if (has(spanish, meaning,"устанавливать","instalar","обновлять","actualizar")) return "⬇️";
        if (has(spanish, meaning,"подключ","conectar")) return "🔌";
        if (has(spanish, meaning,"выключ","apagar","apagado")) return "⏻";
        if (has(spanish, meaning,"включ","encender","encendido")) return "💡";
        if (has(spanish, meaning,"заряж","cargar","cargado")) return "🔋";
        if (has(spanish, meaning,"скачив","descargar")) return "⬇️";
        if (has(spanish, meaning,"удал","borrar")) return "🗑️";
        if (has(spanish, meaning,"копир","copiar")) return "📋";
        if (has(spanish, meaning,"провер","comprobar","revisar")) return "✅";
        if (has(spanish, meaning,"перевод","traducir")) return "🌐";
        if (has(spanish, meaning,"повтор","repetir")) return "🔁";
        if (has(spanish, meaning,"запомин","memorizar","помнить","recordar")) return "🧠";

        if (has(spanish, meaning,"счастлив","feliz","довольн","contento","весёл","alegre")) return "😄";
        if (has(spanish, meaning,"груст","triste")) return "😔";
        if (has(spanish, meaning,"злой","enojado","сердит","enfadado")) return "😠";
        if (has(spanish, meaning,"страш","asustado")) return "😨";
        if (has(spanish, meaning,"удив","sorprendido")) return "😲";
        if (has(spanish, meaning,"устав","cansado")) return "🥱";
        if (has(spanish, meaning,"нерв","nervioso","тревож","ansioso")) return "😰";
        if (has(spanish, meaning,"спокой","tranquilo","calmado","sereno")) return "😌";
        if (has(spanish, meaning,"скуч","aburrido")) return "🥱";
        if (has(spanish, meaning,"больш","grande")) return "🐘";
        if (has(spanish, meaning,"малень","pequeño")) return "🐭";
        if (has(spanish, meaning,"высок","alto")) return "🦒";
        if (has(spanish, meaning,"низк","bajo")) return "🐢";
        if (has(spanish, meaning,"длин","largo")) return "📏";
        if (has(spanish, meaning,"корот","corto")) return "✂️";
        if (has(spanish, meaning,"сильн","fuerte","potente")) return "💪";
        if (has(spanish, meaning,"слаб","débil")) return "🪶";
        if (has(spanish, meaning,"дорог","caro","costoso")) return "💎";
        if (has(spanish, meaning,"дешёв","barato","económico")) return "🏷️";
        if (has(spanish, meaning,"чист","limpio")) return "✨";
        if (has(spanish, meaning,"гряз","sucio")) return "🧹";
        if (has(spanish, meaning,"открыт","abierto")) return "🔓";
        if (has(spanish, meaning,"закрыт","cerrado")) return "🔒";
        if (has(spanish, meaning,"опас","peligroso","arriesgado")) return "⚠️";
        if (has(spanish, meaning,"безопас","seguro")) return "🛡️";
        if (has(spanish, meaning,"правиль","correcto","точн","exacto","preciso")) return "✅";
        if (has(spanish, meaning,"ошиб","equivocado","incorrecto")) return "❌";
        if (has(spanish, meaning,"важн","importante","главн","principal")) return "⭐";
        if (has(spanish, meaning,"нов","nuevo")) return "🆕";
        if (has(spanish, meaning,"стар","viejo","antiguo")) return "🕰️";
        if (has(spanish, meaning,"молод","joven")) return "🌱";
        if (has(spanish, meaning,"красив","bonito","hermoso","lindo","precioso")) return "🌸";
        if (has(spanish, meaning,"некрас","feo","ужас","horrible","terrible")) return "🌩️";
        if (has(spanish, meaning,"бел","blanco")) return "⚪";
        if (has(spanish, meaning,"чёрн","negro")) return "⚫";
        if (has(spanish, meaning,"красн","rojo")) return "🔴";
        if (has(spanish, meaning,"зелён","verde")) return "🟢";
        if (has(spanish, meaning,"син","azul")) return "🔵";
        if (has(spanish, meaning,"жёлт","amarillo")) return "🟡";
        if (has(spanish, meaning,"слад","dulce")) return "🍯";
        if (has(spanish, meaning,"солён","salado")) return "🧂";
        if (has(spanish, meaning,"горь","amargo")) return "☕";
        if (has(spanish, meaning,"остр","picante")) return "🌶️";
        if (has(spanish, meaning,"кисл","ácido")) return "🍋";

        if (card.kind() == 1) return "💬";
        return switch (card.category()) {
            case 1 -> "🎬";
            case 2 -> "🖼️";
            case 3 -> "🎭";
            default -> "💡";
        };
    }

    private static boolean has(String spanish, String meaning, String... needles) {
        for (String raw : needles) {
            String n = raw.toLowerCase(Locale.ROOT);
            if (n.matches(".*[а-яё].*")) {
                if (meaning.contains(n)) return true;
            } else if (spanish.equals(n)) {
                return true;
            } else if ((n.contains(" ") || n.contains("+")) && spanish.contains(n)) {
                return true;
            } else if (n.length() >= 5 && spanish.startsWith(n)) {
                return true;
            }
        }
        return false;
    }
}
