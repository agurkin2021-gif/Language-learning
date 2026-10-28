package ru.spanish.learning;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** One-time family story installed in Spanish for Mama and Dima only. */
final class FamilyStory {
    private FamilyStory() {}
    static final String TITLE = "La mamá buena, Olya y Baksik";
    static final String SOURCE = "Семейный рассказ • аргентинский испанский";
    private static final String FLAG = "family_story_baksik_20261028";

    static final String BODY = """
        Era sábado. Mamá estaba en la cocina y preparaba el desayuno. Puso pan, queso, un huevo y una taza de té en la mesa.
        
        Olya estaba en el sofá con su celular. Baksik estaba al lado de la puerta, con un zapato de Olya en la boca.
        
        Mamá miró a Baksik y después a Olya.
        
        —Olya, Baksik quiere salir. ¿Vas a caminar con él?
        
        —No, mamá. Quiero descansar. El viernes estudié mucho y estoy cansada.
        
        —El viernes también querías descansar —dijo mamá.
        
        —¡Sí! ¡Pero el sábado estoy más cansada!
        
        Mamá se rió.
        
        —Entonces, ¿quién va a salir con Baksik?
        
        —Vos, mamá. Sos muy buena.
        
        —Y vos sos muy inteligente. Seguro que vas a encontrar una buena idea.
        
        Olya miró a Baksik.
        
        —¡Baksik! ¡Ese zapato es mío!
        
        Baksik fue a la puerta y dejó el zapato en el piso. Después miró a Olya.
        
        —Mamá, ¡el perro manda en esta casa!
        
        —No —dijo mamá—. Pero este sábado podés mandar vos.
        
        —¿Yo?
        
        —Sí. Vos mandás.
        
        Olya estaba feliz.
        
        —¡Perfecto! Primero voy a tomar té. Después voy a leer un libro. Y vos vas a salir con Baksik.
        
        —Perfecto —dijo mamá—. Pero vos vas a limpiar la cocina, preparar el almuerzo y comprar pan.
        
        —¿Todo eso?
        
        —Claro. Vos mandás. Y también trabajás vos.
        
        Olya pensó un momento.
        
        —Entonces voy a empezar con algo fácil. ¡Baksik, sentate!
        
        Baksik se sentó.
        
        —¡Perfecto! —dijo Olya—. ¡Baksik, andá a caminar solo!
        
        Baksik no se movió. Miró a Olya y después a la puerta.
        
        —¡Qué perro tan difícil!
        
        —Baksik no puede salir solo —dijo mamá—. Vos tenés que salir con él.
        
        Olya fue a buscar sus zapatos. Pero encontró solo uno.
        
        —Mamá, perdí un zapato.
        
        —Buscá al lado de la puerta.
        
        Olya miró al perro. Baksik estaba sentado sobre el otro zapato.
        
        —¡Qué inteligente! —dijo Olya—. Yo tengo dos zapatos, pero Baksik tiene los dos.
        
        Mamá se rió.
        
        —Parece que Baksik tiene una buena idea.
        
        Olya tomó sus zapatos y salió con Baksik.
        
        En la calle, Baksik corrió muy rápido. Olya empezó a correr también.
        
        —¡Baksik, esperá! ¡Yo no soy un perro!
        
        Baksik paró. Después vio un gato al lado de un árbol.
        
        El gato subió al árbol. Baksik miró al gato. El gato miró a Baksik.
        
        —No, Baksik. Ese gato no quiere jugar con vos.
        
        Pero Baksik saltó una vez. Después saltó otra vez.
        
        —¡No, Baksik! ¡El gato quiere descansar, como yo!
        
        Una vecina vio a Olya.
        
        —¿Por qué corrés tanto, Olya?
        
        —Porque Baksik corre muy rápido.
        
        —¿Y vos no querías salir?
        
        —No quería. Pero estoy contenta.
        
        —Entonces mañana vas a salir otra vez.
        
        —¡Mañana voy a dormir! —dijo Olya.
        
        Baksik encontró una flor y la llevó a Olya.
        
        —¿Es para mí? ¡Qué linda, Baksik!
        
        Olya tomó la flor.
        
        Pero Baksik vio una mariposa y empezó a correr otra vez.
        
        —¡Baksik! ¡Primero vamos al parque!
        
        En el parque, Baksik caminó al lado de Olya. Olya tomó una foto del perro.
        
        Después se sentó al lado de un árbol. Baksik se sentó también.
        
        Olya miró a Baksik.
        
        —Sos un buen perro, Baksik. Pero caminás mucho.
        
        Baksik puso su cabeza sobre la pierna de Olya.
        
        —¿Estás cansado? —preguntó Olya.
        
        Baksik cerró los ojos.
        
        —¡Perfecto! ¡Vamos a casa!
        
        Pero Baksik abrió los ojos y empezó a correr otra vez.
        
        —¡No! —dijo Olya—. ¡Vos no estás cansado! ¡Yo estoy cansada!
        
        Olya y Baksik volvieron a casa.
        
        Mamá estaba en el sofá y leía un libro.
        
        —¿Cómo fue todo? —preguntó mamá.
        
        —Baksik corrió. Yo corrí. Un gato subió a un árbol. Una vecina habló conmigo. Y Baksik encontró una flor.
        
        —¿Y vos estás contenta?
        
        —Sí. Pero estoy muy cansada.
        
        —¿Fue horrible?
        
        —No —dijo Olya—. Fue divertido.
        
        Olya abrazó a Baksik.
        
        Después miró a mamá.
        
        —Mañana voy a caminar con Baksik otra vez.
        
        Mamá abrió los ojos.
        
        —¿En serio?
        
        —Sí. Pero primero vos vas a preparar el desayuno.
        
        —Eso ya lo hago.
        
        —Y vas a lavar los platos.
        
        —Eso también lo hago.
        
        —Y vas a limpiar la cocina.
        
        —También lo hago.
        
        Olya pensó un momento.
        
        —Entonces mañana mandás vos. Yo voy a ayudar un poco.
        
        Mamá se rió.
        
        Baksik fue a la puerta con un zapato de Olya.
        
        —¡No, Baksik! ¡Ya salimos!
        
        —Parece que quiere salir otra vez —dijo mamá.
        
        —Bueno, pero esta vez vos vas con él. ¡Yo voy a descansar!
        
        Olya guardó los zapatos en el armario.
        
        —Mañana no los vas a encontrar, Baksik.
        
        Pero Baksik fue al armario y se sentó al lado de la puerta.
        
        Olya miró al perro.
        
        —¡Mamá! ¡Creo que este perro es más inteligente que yo!
        
        Mamá se rió.
        
        —Olya, Baksik es un buen perro.
        
        —Sí, mamá. Pero tiene un problema.
        
        —¿Qué problema?
        
        —¡Quiere caminar todo el día!
        
        Mamá abrazó a Olya.
        
        —¿Y vos qué querés?
        
        —Quiero descansar. Quiero comer. Y quiero un helado.
        
        —¿Un helado?
        
        —¡Sí! ¡Yo caminé mucho!
        
        Mamá se rió otra vez.
        
        —Bueno, mañana vamos a comprar un helado.
        
        Olya estaba feliz.
        
        —¡Perfecto! ¡Mañana voy a salir con Baksik!
        
        Baksik miró a Olya y corrió a la puerta.
        
        —¡No, Baksik! —dijo Olya—. ¡Mañana, no este sábado!
        """;

    static final String RUSSIAN = """
        Была суббота. Мама была на кухне и готовила завтрак. Она поставила на стол хлеб, сыр, яйцо и чашку чая.
        
        Оля сидела на диване с телефоном. Баксик был у двери с Олиным ботинком во рту.
        
        Мама посмотрела на Баксика, а потом на Олю.
        
        —Оля, Баксик хочет на улицу. Ты пойдёшь с ним гулять?
        
        —Нет, мама. Я хочу отдохнуть. В пятницу я много занималась и устала.
        
        —В пятницу ты тоже хотела отдыхать, — сказала мама.
        
        —Да! Но в субботу я ещё больше устала!
        
        Мама рассмеялась.
        
        —И кто же пойдёт гулять с Баксиком?
        
        —Ты, мама. Ты ведь очень добрая.
        
        —А ты очень умная. Уверена, ты придумаешь хорошую идею.
        
        Оля посмотрела на Баксика.
        
        —Баксик! Это мой ботинок!
        
        Баксик подошёл к двери и положил ботинок на пол. Потом посмотрел на Олю.
        
        —Мама, собака здесь командует!
        
        —Нет, — сказала мама. Но в эту субботу командовать можешь ты.
        
        —Я?
        
        —Да. Ты командуешь.
        
        Оля была счастлива.
        
        —Отлично! Сначала я выпью чай. Потом почитаю книгу. А ты пойдёшь гулять с Баксиком.
        
        —Отлично, — сказала мама. Но ты будешь убирать кухню, готовить обед и покупать хлеб.
        
        —Всё это?
        
        —Конечно. Ты командуешь. И ты же работаешь.
        
        Оля немного подумала.
        
        —Тогда я начну с чего-нибудь лёгкого. Баксик, сядь!
        
        Баксик сел.
        
        —Отлично! — сказала Оля. Баксик, иди гуляй один!
        
        Баксик не двигался. Посмотрел на Олю, а потом на дверь.
        
        —Какой трудный пёс!
        
        —Баксик не может выходить один, — сказала мама. Тебе нужно идти с ним.
        
        Оля пошла искать свои ботинки. Но нашла только один.
        
        —Мама, я потеряла ботинок.
        
        —Поищи рядом с дверью.
        
        Оля посмотрела на собаку. Баксик сидел на другом ботинке.
        
        —Какой умный! — сказала Оля. У меня два ботинка, но оба у Баксика.
        
        Мама рассмеялась.
        
        —Похоже, у Баксика есть хорошая идея.
        
        Оля взяла ботинки и вышла с Баксиком.
        
        На улице Баксик побежал очень быстро. Оля тоже побежала.
        
        —Баксик, подожди! Я же не собака!
        
        Баксик остановился. Потом увидел кота возле дерева.
        
        Кот забрался на дерево. Баксик посмотрел на кота. Кот посмотрел на Баксика.
        
        —Нет, Баксик. Этот кот не хочет с тобой играть.
        
        Но Баксик прыгнул один раз. Потом прыгнул ещё раз.
        
        —Нет, Баксик! Кот хочет отдохнуть, как и я!
        
        Соседка увидела Олю.
        
        —Почему ты так бежишь, Оля?
        
        —Потому что Баксик очень быстро бегает.
        
        —А ты разве не хотела оставаться дома?
        
        —Не хотела гулять. Но теперь я довольна.
        
        —Значит, завтра опять пойдёшь.
        
        —Завтра я буду спать! — сказала Оля.
        
        Баксик нашёл цветок и принёс его Оле.
        
        —Это мне? Какой красивый, Баксик!
        
        Оля взяла цветок.
        
        Но Баксик увидел бабочку и снова побежал.
        
        —Баксик! Сначала пойдём в парк!
        
        В парке Баксик шёл рядом с Олей. Оля сфотографировала собаку.
        
        Потом она села возле дерева. Баксик тоже сел.
        
        Оля посмотрела на Баксика.
        
        —Ты хороший пёс, Баксик. Но очень много гуляешь.
        
        Баксик положил голову Оле на ногу.
        
        —Ты устал? — спросила Оля.
        
        Баксик закрыл глаза.
        
        —Отлично! Пойдём домой!
        
        Но Баксик открыл глаза и снова побежал.
        
        —Нет! — сказала Оля. Ты не устал! А я устала!
        
        Оля и Баксик вернулись домой.
        
        Мама сидела на диване и читала книгу.
        
        —Как всё прошло? — спросила мама.
        
        —Баксик бегал. Я бегала. Кот залез на дерево. Со мной поговорила соседка. А Баксик нашёл цветок.
        
        —И ты довольна?
        
        —Да. Но я очень устала.
        
        —Было ужасно?
        
        —Нет, — сказала Оля. Было весело.
        
        Оля обняла Баксика.
        
        Потом посмотрела на маму.
        
        —Завтра я опять пойду гулять с Баксиком.
        
        Мама широко открыла глаза.
        
        —Серьёзно?
        
        —Да. Но сначала ты приготовишь завтрак.
        
        —Я и так это делаю.
        
        —И помоешь посуду.
        
        —И это я тоже делаю.
        
        —И уберёшь кухню.
        
        —И это я делаю.
        
        Оля немного подумала.
        
        —Тогда завтра командуешь ты. А я немного помогу.
        
        Мама засмеялась.
        
        Баксик подошёл к двери с Олиным ботинком.
        
        —Нет, Баксик! Мы уже гуляли!
        
        —Похоже, он снова хочет на улицу, — сказала мама.
        
        —Хорошо, но на этот раз пойдёшь ты. Я буду отдыхать!
        
        Оля убрала ботинки в шкаф.
        
        —Завтра ты их не найдёшь, Баксик.
        
        Но Баксик подошёл к шкафу и сел возле двери.
        
        Оля посмотрела на собаку.
        
        —Мама! По-моему, этот пёс умнее меня!
        
        Мама рассмеялась.
        
        —Оля, Баксик хороший пёс.
        
        —Да, мама. Но у него есть одна проблема.
        
        —Какая проблема?
        
        —Он хочет гулять весь день!
        
        Мама обняла Олю.
        
        —А чего хочешь ты?
        
        —Я хочу отдохнуть. Хочу поесть. И хочу мороженое.
        
        —Мороженое?
        
        —Да! Я так много гуляла!
        
        Мама снова рассмеялась.
        
        —Хорошо, завтра купим мороженое.
        
        Оля была счастлива.
        
        —Отлично! Завтра я пойду гулять с Баксиком!
        
        Баксик посмотрел на Олю и побежал к двери.
        
        —Нет, Баксик! — сказала Оля. Завтра, не в эту субботу!
        """;

    /** Never add again after deletion, explicit reset, or a prior matching story. */
    static void seed(SQLiteDatabase db) {
        if (hasFlag(db, FLAG)) return;
        if (!hasFlag(db, "personal_story_reset") && !exists(db)) {
            if (Sentences.split(BODY).size() != Sentences.split(RUSSIAN).size())
                throw new IllegalStateException("Baksik story and translation are not aligned");
            ContentValues story = new ContentValues();
            story.put("title", TITLE);
            story.put("body", BODY.strip());
            story.put("translation", RUSSIAN.strip());
            story.put("source", SOURCE);
            db.insertOrThrow("stories", null, story);
        }
        ContentValues flag = new ContentValues();
        flag.put("key", FLAG);
        flag.put("value", 1);
        db.insertWithOnConflict("app_state", null, flag, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static boolean exists(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM stories WHERE title=? LIMIT 1",
                new String[]{TITLE})) {
            return c.moveToFirst();
        }
    }

    private static boolean hasFlag(SQLiteDatabase db, String key) {
        try (Cursor c = db.rawQuery("SELECT value FROM app_state WHERE key=?",
                new String[]{key})) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }
}
