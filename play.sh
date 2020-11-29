#!/bin/bash

OWNER_JWT=eyJjdHkiOiJKV1QiLCJlbmMiOiJBMjU2R0NNIiwiYWxnIjoiZGlyIn0..m6TmnE8WYdxLKNk0.Zde-0ZU-8M8yBTMNIIifn4v2H3I7dPZWQFHYt26oPicZABAyxMolEDKaPY4t79byJ-v8OSK2AUgtEGT4y64widwjjsJJ_uoUOCm6cVd1vfTWD8daRULal02QaeVxqy6Rsw4FEiPqvmKy73qEIvLpztdDE_qWi6PIngpMHlg9XfXqsf6H_hkHE2q47SmLCQzu7tIIPQN_c-I21Mqd1FajeA.vMxlrg_x4we5exOBxGem2A
PLAYER_JWT=eyJjdHkiOiJKV1QiLCJlbmMiOiJBMjU2R0NNIiwiYWxnIjoiZGlyIn0..BPKk6Mc7TqIobEfS.i9t4nfyRRKt9UaZTaSNoJSLdphDrkH_o8NEJ7sRz7TPgzO5F5lgPT2bcNH6YBKK02QxJmq4bi7oW7CSwj94s29MOm-F3Vqh1M_80jGXmDC1ASk2OV6Y3KCjKK5zQ_70A9HDOI58BPFBEoKwmrmGA9b6yGQ1agyk7LZ6y1YvtsHtUOtoQ06Z-4wE72EjJXrMt75vjyd09OqrkRyixFRqF6COvWtXlPhtF_Zo2577bjDBk3uIonG7ejAa_7BneRGm_KpfhlD6hbDwqPyuqpRhN01fTOQP4KK_QxsBnP7SRzsY7ouGabzm2bR4a9bOD5tgeZmyLxWgMVQQ.-tCXQsReE2DpnqPocLu4YA

echo "Creating game"
GAME_ID=$(curl -X POST -s 'http://localhost:9000/api/game?name=wild-west' -H "Authorization: Bearer $OWNER_JWT" | jq -r .message)
echo "Game created $GAME_ID"
curl -s "http://localhost:9000/api/game/$GAME_ID" | jq .

echo "Player joins the game"
curl -s -X PATCH "http://localhost:9000/api/game/$GAME_ID/join" -H "Authorization: Bearer $PLAYER_JWT"  | jq .
curl -s "http://localhost:9000/api/game/$GAME_ID" | jq .

echo "Owner starts the game"
curl -s -X PATCH "http://localhost:9000/api/game/$GAME_ID/start" -H "Authorization: Bearer $OWNER_JWT"  | jq .
curl -s "http://localhost:9000/api/game/$GAME_ID" | jq .


