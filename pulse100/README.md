# Pulse 100

Jogo Android de reação em cadeia cromática feito em Godot 4 e GDScript. Toda a arte é desenhada por código em tempo real.

## Conteúdo

- controle de um único toque;
- pulsos cromáticos, prismas e partículas carregadas;
- partidas de três rodadas;
- progressão infinita no modo clássico;
- desafio diário determinístico e offline;
- recordes e configurações persistentes;
- vibração e modo de efeitos reduzidos;
- suporte Android ARMv7 e ARM64.

## Executar no computador

```bash
godot --path .
```

## Gerar APK de teste

```bash
godot --headless --path . --export-debug Android build/Pulse100.apk
```

## Build automatizada

A integração contínua valida os scripts e gera um APK Android instalável a cada alteração do projeto.
