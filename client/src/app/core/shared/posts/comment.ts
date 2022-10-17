export class Comment {
    id: string;
    author: Author;
    content: string;
    dislikes: number;
    likes: number;
    repliedTo: any;
    timestamp: Date;
}

export class Author {
    email: string;
    name: string;
    nick: string;
    id: string;
}