이 폴더는 "로컬 전용" Docker 이미지를 tar로 받아올 때 사용합니다.

선택: frontend 이미지를 다른 머신에서 export 했다면 파일명을 아래처럼 두고
  infra/scripts/load-local-images.sh (또는 .ps1)
를 실행하면 자동으로 docker load 됩니다.

  frontend-0.0.7.tar   → docker load 후 이미지 이름이 frontend:0.0.7 이어야 합니다.
                         (다르면: docker tag 로 맞춤)

직접 주입만 할 때:
  docker load -i frontend-0.0.7.tar
  docker images | findstr frontend
