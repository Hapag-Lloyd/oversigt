import { Component, OnInit } from '@angular/core';
import { AuthenticationService, Configuration } from 'src/oversigt-client';
import { NzMessageService } from 'ng-zorro-antd';
import { Router } from '@angular/router';

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
    private authenticationService: AuthenticationService,
    private message: NzMessageService,
    private configuration: Configuration,
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
    this.authenticationService.authenticateUser(this.username, this.password).subscribe(
      ok => {
        const authorization = 'Bearer ' + ok['token'];
        this.configuration.apiKeys['Authorization'] = authorization;
        this.router.navigateByUrl('/config');
      },
      fail => {
        if (fail.status === 403) {
          this.password = '';
          this.message.error('Login failed.');
        } else {
          console.error(fail);
          // TODO
        }
      },
      () => {
        this.loginButtonEnabled = true;
      }
    );
  }
}
