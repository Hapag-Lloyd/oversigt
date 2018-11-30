import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { UserService } from '../user-service.service';
import { NotificationService } from '../notification.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  username = '';
  password = '';
  loginButtonEnabled = true;

  constructor(
    private message: NotificationService,
    private user: UserService,
    private router: Router,
  ) { }

  ngOnInit() {
  }

  doLogin() {
    if (this.username === '' || this.password === '') {
      this.message.error('Please enter username and password.');
      return;
    }

    this.loginButtonEnabled = false;
    const password = this.password;
    this.password = '';
    this.user.logIn(this.username, password,
      name => { // success
        this.username = '';
        this.router.navigateByUrl(this.user.requestedUrl, {replaceUrl: true});
      }, () => { // fail
        this.message.error('Login failed.');
      }, () => { // done
        this.loginButtonEnabled = true;
      }
    );
  }
}
