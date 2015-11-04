stage 'test approve'
node ('swarm'){
  ws ('approve'){

    hubotApprove message: "Do you want to stage?", room: "release"
    input id: 'Proceed', message: "Staging?"


  }
}
